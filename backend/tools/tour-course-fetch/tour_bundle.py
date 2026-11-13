"""통과 코스로 묶음 파일 만들기(Jira S15P31A109-222, 명세 tour-course-seed.md 6-3·6-7).

고른 코스의 소개 글(detailCommon2, 투어API 호출 상한에 포함)·장소 사진(원본 그대로)·지역 코드(카카오 법정동)를 붙여
bundles/{bundleId}/ 아래에 bundle-{bundleId}.json 과 photos/{bundleId}/ 를 만든다. 서버에는 이 폴더 내용을 S3 course-seed/ 아래로 올린다.
"""

import hashlib
import html
import json
import os
import re
import shutil
import urllib.error
import urllib.parse
import urllib.request
from collections import Counter
from datetime import datetime

from tour_api import BudgetExhausted, StopRun, TourApi, items_of

DEFAULT_KAKAO_BASE_URL = "https://dapi.kakao.com"
PHOTO_MAX_BYTES = 20 * 1024 * 1024
SIGNATURES = ((b"\xff\xd8\xff", "jpg"), (b"\x89PNG\r\n\x1a\n", "png"))


def clean_text(text):
    """투어API 글의 <br>·태그·HTML 엔티티를 정리한다. 비면 None."""
    if not text:
        return None
    text = re.sub(r"<br\s*/?>", "\n", text, flags=re.IGNORECASE)
    text = html.unescape(re.sub(r"<[^>]+>", "", text))
    lines = [re.sub(r"[ \t ]+", " ", line).strip() for line in text.splitlines()]
    text = re.sub(r"\n{3,}", "\n\n", "\n".join(lines)).strip()
    return text or None


def image_extension(data):
    for signature, ext in SIGNATURES:
        if data.startswith(signature):
            return ext
    if len(data) >= 12 and data[0:4] == b"RIFF" and data[8:12] == b"WEBP":
        return "webp"
    return None


def download(url, timeout=15.0):
    """사진 원본 바이트. 실패하면 None(키가 없는 공개 주소라 주소를 로그에 남겨도 되지만 남기지 않는다)."""
    try:
        with urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": "yeogirok-tour-bundle"}),
                                    timeout=timeout) as response:
            data = response.read(PHOTO_MAX_BYTES + 1)
        return data if 0 < len(data) <= PHOTO_MAX_BYTES else None
    except (urllib.error.URLError, OSError, ValueError):
        return None


class Kakao:
    """좌표 → 법정동 10자리·읍면동 이름(coord2regioncode, region_type B). 키가 없거나 실패하면 (None, None)."""

    def __init__(self, key, base_url=DEFAULT_KAKAO_BASE_URL, timeout=5.0):
        self.key = key
        self.base_url = base_url.rstrip("/")
        self.timeout = timeout
        self.calls = 0

    def region(self, mapx, mapy):
        if not self.key:
            return None, None
        query = urllib.parse.urlencode({"x": mapx, "y": mapy})
        request = urllib.request.Request(f"{self.base_url}/v2/local/geo/coord2regioncode.json?{query}",
                                         headers={"Authorization": f"KakaoAK {self.key}"})
        self.calls += 1
        try:
            with urllib.request.urlopen(request, timeout=self.timeout) as response:
                documents = json.loads(response.read().decode("utf-8")).get("documents") or []
        except (urllib.error.URLError, OSError, ValueError):
            return None, None
        for document in documents:
            if document.get("region_type") == "B":
                return document.get("code"), (document.get("region_3depth_name") or None)
        return None, None


class Bundler:
    def __init__(self, store, fetcher, api, kakao, log=print):
        self.store = store
        self.fetcher = fetcher
        self.api = api
        self.kakao = kakao
        self.log = log
        self.regions = None  # 지역 코드 캐시. 묶음 하나에 한 번 읽고 save_regions 로 한 번 쓴다(PR #130 리뷰)
        self.regions_changed = False

    def index(self):
        return self.store.read("bundles", "index.json", default={"bundles": []})

    def bundled_ids(self):
        return {cid for bundle in self.index()["bundles"] for cid in bundle["contentIds"]}

    def select(self, ids, count, seed):
        """--ids 면 그 코스(통과한 것만), 아니면 처리 순서대로 앞 묶음에 없는 통과 코스 count 개."""
        passed = {j["contentId"]: j for j in self.store.all_judged() if j["result"] == "PASS"}
        if ids:
            missing = [cid for cid in ids if cid not in passed]
            if missing:
                self.log(f"[경고] 통과하지 않았거나 없는 코스는 뺍니다: {missing}")
            return [passed[cid] for cid in ids if cid in passed]
        done = self.bundled_ids()
        ordered = self.fetcher.courses(self.fetcher.dictionary(), seed)
        chosen = [passed[str(c["contentid"])] for c in ordered if str(c["contentid"]) in passed
                  and str(c["contentid"]) not in done]
        return chosen[:count]

    def overview(self, content_id):
        common = self.store.read("courses", content_id, "common.json")
        if common is None:
            common = self.api.get("detailCommon2", contentId=content_id)
            self.store.write(common, "courses", content_id, "common.json")
        return clean_text(((items_of(common) or [{}])[0]).get("overview"))

    def photo(self, place):
        """사진 캐시(data/photo-cache)에서, 없으면 내려받아 캐시에 둔다. (상대 파일명, sha256) 또는 (None, None)."""
        cache = self.store.root / "photo-cache"
        cached = sorted(cache.glob(f"{place['contentId']}.*")) if cache.exists() else []
        if cached:
            data = cached[0].read_bytes()
        else:
            data = download(place["firstimage"]) if place.get("firstimage") else None
            ext = image_extension(data) if data else None
            if not ext:
                return None, None
            cache.mkdir(parents=True, exist_ok=True)
            (cache / f"{place['contentId']}.{ext}").write_bytes(data)
        ext = image_extension(data)
        return (f"{place['contentId']}.{ext}", hashlib.sha256(data).hexdigest()) if ext else (None, None)

    def region(self, place):
        if self.regions is None:
            self.regions = self.store.read("region-cache.json", default={})
        if place["contentId"] not in self.regions:
            code, name = self.kakao.region(place.get("mapx"), place.get("mapy"))
            if code is None:
                return None, None  # 키가 없거나 실패하면 캐시에 남기지 않는다(다음에 다시)
            self.regions[place["contentId"]] = {"code": code, "name": name}
            self.regions_changed = True
        found = self.regions[place["contentId"]]
        return found["code"], found["name"]

    def save_regions(self):
        if self.regions_changed:
            self.store.write(self.regions, "region-cache.json")
            self.regions_changed = False

    def build(self, judged, bundle_id):
        """코스 하나를 묶음 모양으로. 사진을 못 받아 어떤 날의 사진이 0장이면 (None, 사유)."""
        places = []
        photo_files = []
        for p in judged["places"]:
            file_name, digest = self.photo(p) if p["hasPhoto"] else (None, None)
            code, name = self.region(p)
            places.append({
                "contentId": p["contentId"], "name": clean_text(p["name"]) or p["name"],
                "overview": clean_text(p.get("overview")), "day": p["day"], "order": p["order"],
                "visitDate": p["visitDate"], "visitTime": p["visitTime"], "mapx": p["mapx"], "mapy": p["mapy"],
                "address": " ".join(part for part in (p.get("addr1"), p.get("addr2")) if part).strip() or None,
                "regionCode": code, "regionName": name, "lclsSystm2": p.get("lclsSystm2"),
                "lclsSystm3": p.get("lclsSystm3"), "imageUrl": p.get("firstimage") or None,
                "cpyrhtDivCd": p.get("cpyrhtDivCd") or None,
                # 묶음별 폴더: S3 course-seed/photos/ 에 여러 묶음을 올려도 같은 장소 사진이 겹치지 않게(PR #130 리뷰)
                "photo": f"photos/{bundle_id}/{file_name}" if file_name else None, "photoSha256": digest,
            })
            if file_name:
                photo_files.append(file_name)
        days = {p["day"] for p in places}
        if any(not any(p["photo"] for p in places if p["day"] == day) for day in days):
            return None, "PHOTO_DOWNLOAD", []
        course = {"contentId": judged["contentId"], "title": clean_text(judged["title"]) or judged["title"],
                  "overview": self.overview(judged["contentId"]), "days": judged["days"], "places": places}
        return course, None, photo_files


def next_bundle_id(index):
    today = datetime.now().strftime("%Y%m%d")
    taken = [b["bundleId"] for b in index["bundles"] if b["bundleId"].startswith(today + "-")]
    return f"{today}-{len(taken) + 1:02d}"


def cmd_bundle(args, log, fetcher_class, store):
    if not args.ids and not args.count:
        log("--ids 또는 --count 중 하나를 주세요.")
        return 1
    key = os.environ.get("TOUR_API_KEY")
    kakao_key = os.environ.get("KAKAO_REST_API_KEY")
    if not kakao_key:
        log("[주의] KAKAO_REST_API_KEY 가 없어 지역 코드(regionCode·regionName)를 비워 둡니다.")
    api = TourApi(key or "", args.max_calls, base_url=args.base_url, interval=args.interval,
                  retry_waits=(args.retry_wait, args.retry_wait * 3), log=log)
    fetcher = fetcher_class(store, api, args.dict_rows, args.info_rows, log)
    if fetcher.dictionary() is None:
        log("장소 사전이 다 받아지지 않아 묶음을 만들 수 없습니다.")
        return 1
    bundler = Bundler(store, fetcher, api, Kakao(kakao_key, args.kakao_base_url), log)
    index = bundler.index()
    bundle_id = args.bundle_id or next_bundle_id(index)
    if any(b["bundleId"] == bundle_id for b in index["bundles"]):
        log(f"이미 있는 묶음 ID 입니다: {bundle_id}")
        return 1
    ids = [cid.strip() for cid in (args.ids or "").split(",") if cid.strip()]
    chosen = bundler.select(ids, args.count, store.state().get("seed") or args.seed)
    log(f"묶음 {bundle_id}: 고른 코스 {len(chosen)}개. 소개 글은 투어API 최대 {args.max_calls}번(캐시는 안 부름).")
    if chosen and not key and any(store.read("courses", j["contentId"], "common.json") is None for j in chosen):
        log("TOUR_API_KEY 환경 변수가 없어 소개 글을 받지 못합니다.")
        return 1
    courses, dropped, photos = [], {}, []
    try:
        for judged in chosen:
            course, reason, files = bundler.build(judged, bundle_id)
            if course is None:
                dropped[judged["contentId"]] = reason
                continue
            courses.append(course)
            photos.extend(files)
    except (BudgetExhausted, StopRun) as e:
        detail = e.to_dict() if isinstance(e, StopRun) else {"kind": "MAX_CALLS"}
        log(f"[멈춤] 소개 글을 다 받지 못해 묶음을 만들지 않았습니다: {detail}. 받은 소개 글은 저장해 두었습니다.")
        return 1
    finally:
        bundler.save_regions()
        state = store.state()
        state["calls"] = state.get("calls", 0) + api.calls
        store.save_state(state)
    folder = store.root / "bundles" / bundle_id
    (folder / "photos" / bundle_id).mkdir(parents=True, exist_ok=True)
    for name in photos:
        shutil.copyfile(store.root / "photo-cache" / name, folder / "photos" / bundle_id / name)
    bundle = {"bundleId": bundle_id, "createdAt": datetime.now().astimezone().isoformat(timespec="seconds"),
              "source": "tour-course-fetch", "courses": courses}
    store.write(bundle, "bundles", bundle_id, f"bundle-{bundle_id}.json")
    index["bundles"].append({"bundleId": bundle_id, "contentIds": [c["contentId"] for c in courses],
                             "createdAt": bundle["createdAt"]})
    store.write(index, "bundles", "index.json")
    regions = Counter((p["regionCode"] or "(없음)")[:2] for c in courses for p in c["places"])
    log(f"묶음 만듦: {folder}  코스 {len(courses)}개, 사진 {len(photos)}장, 뺀 코스 {dropped or '없음'}")
    log(f"지역 코드 앞 2자리(카카오): {dict(regions)} · 투어API 호출 {api.calls}번 · 카카오 호출 {bundler.kakao.calls}번")
    return 0
