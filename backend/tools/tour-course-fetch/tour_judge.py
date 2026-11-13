"""코스 판정·일수·일차 배분·날짜·시각(명세 tour-course-seed.md 3-7). 호출하지 않는다.

배분·날짜·시각은 여기서만 계산하고 결과를 judged/{id}.json 에 적는다. 2단계(백엔드)는 다시 계산하지 않고 그대로 읽는다(3-8).
"""

import re
from collections import Counter
from datetime import date, timedelta

EMPTY_INFO = "EMPTY_INFO"
FEW_VALID = "FEW_VALID"
NO_PHOTO = "NO_PHOTO"
SPLIT_FAIL = "SPLIT_FAIL"
NO_DATE = "NO_DATE"
FIRST_STAGE_REASONS = (EMPTY_INFO, FEW_VALID, NO_PHOTO)
MIN_VALID_PLACES = 3

DAY_START_MINUTES = 9 * 60
DAY_SPAN_MINUTES = 12 * 60
HOURLY_MAX_PLACES = 13

PLACE_FIELDS = ("mapx", "mapy", "addr1", "addr2", "lDongRegnCd", "lDongSignguCd",
                "lclsSystm1", "lclsSystm2", "lclsSystm3", "firstimage", "cpyrhtDivCd")


def has_coordinates(item):
    try:
        return float(item.get("mapx") or 0) != 0 and float(item.get("mapy") or 0) != 0
    except (TypeError, ValueError):
        return False


def parse_subnum(item):
    """문자("0","10"…)로 오는 subnum 을 숫자로. 못 읽으면 None."""
    try:
        return int(str(item.get("subnum", "")).strip())
    except ValueError:
        return None


def subnum_key(index_and_item):
    index, item = index_and_item
    number = parse_subnum(item)
    return (float("inf") if number is None else number), index


def valid_places(info_items, dictionary):
    """유효 장소(사전에 있고 좌표가 0 이 아님)를 subnum 숫자순으로, 나머지 장소 ID 와 함께."""
    ordered = [item for _, item in sorted(enumerate(info_items), key=subnum_key)]
    valid, excluded = [], []
    for item in ordered:
        sub_id = str(item.get("subcontentid") or "")
        place = dictionary.get(sub_id)
        if place is not None and has_coordinates(place):
            valid.append((item, place))
        else:
            excluded.append(sub_id)
    return valid, excluded


def has_photo(place):
    return bool((place.get("firstimage") or "").strip())


def first_stage(info_items, dictionary):
    """1차 판정. 통과면 None, 탈락이면 사유."""
    if not info_items:
        return EMPTY_INFO
    valid, _ = valid_places(info_items, dictionary)
    if len(valid) < MIN_VALID_PLACES:
        return FEW_VALID
    if not any(has_photo(place) for _, place in valid):
        return NO_PHOTO
    return None


def parse_days(schedule):
    """(일수, 읽었는지). "당일" 1, "N박M일" M, "M일" M, 그 밖 1."""
    text = re.sub(r"\s+", "", schedule or "")
    if "당일" in text:
        return 1, True
    match = re.search(r"(\d+)박(\d+)일", text)
    if match:
        return max(1, int(match.group(2))), True
    match = re.search(r"(\d+)일", text)
    if match:
        return max(1, int(match.group(1))), True
    return 1, False


def split_days(photos, days):
    """연속으로 잘라 날마다 사진 장소가 1곳 이상이게 한다. (실제 일수, 날별 끝 위치 목록) 또는 None.

    기본은 균등(앞날부터 1곳씩 더). 경계는 조건을 만족하는 위치 중 기본 경계와 가장 가까운 곳(같으면 앞쪽).
    안 되면 일수를 1씩 줄인다.
    """
    n = len(photos)
    for d in range(min(days, n), 0, -1):
        ends = _try_split(photos, d)
        if ends is not None:
            return d, ends
    return None


def _try_split(photos, d):
    n = len(photos)
    suffix = [0] * (n + 1)
    for i in range(n - 1, -1, -1):
        suffix[i] = suffix[i + 1] + (1 if photos[i] else 0)
    base = [n // d + (1 if i < n % d else 0) for i in range(d)]
    start, ends = 0, []
    for k in range(1, d):
        boundary = sum(base[:k])
        remaining = d - k
        candidates = [e for e in range(start + 1, n - remaining + 1)
                      if suffix[start] - suffix[e] > 0 and suffix[e] >= remaining]
        if not candidates:
            return None
        end = min(candidates, key=lambda e: (abs(e - boundary), e))
        ends.append(end)
        start = end
    if suffix[start] == 0:
        return None
    ends.append(n)
    return ends


def visit_times(count):
    """그날 장소 수만큼 "HH:MM". 13곳까지 09:00 부터 1시간 간격, 14곳부터 09:00~21:00 을 고르게."""
    step = 60 if count <= HOURLY_MAX_PLACES else DAY_SPAN_MINUTES // (count - 1)
    return [f"{(DAY_START_MINUTES + i * step) // 60:02d}:{(DAY_START_MINUTES + i * step) % 60:02d}"
            for i in range(count)]


def created_date(course):
    for field in ("createdtime", "modifiedtime"):
        value = str(course.get(field) or "")
        try:
            return date(int(value[0:4]), int(value[4:6]), int(value[6:8]))
        except ValueError:
            continue
    return None


def province_of(places):
    codes = Counter(str(p.get("lDongRegnCd") or "") for p in places if p.get("lDongRegnCd"))
    if not codes:
        return None
    top = max(codes.values())
    return min(code for code, n in codes.items() if n == top)


def judge(course, info_items, info_total, intro_item, dictionary):
    """코스 판정 결과(judged/{id}.json 모양). intro_item 이 None 이면 1차만 본다."""
    result = {
        "contentId": str(course.get("contentid")), "title": course.get("title"),
        "createdDate": None, "schedule": None, "scheduleDays": None, "scheduleRead": None,
        "days": None, "reducedFrom": None, "result": "FAIL", "reason": None,
        "infoTotal": info_total, "infoReceived": len(info_items), "excludedPlaceIds": [],
        "provinceCode": None, "places": [],
    }
    valid, excluded = valid_places(info_items, dictionary)
    result["excludedPlaceIds"] = excluded
    result["provinceCode"] = province_of([place for _, place in valid])
    reason = first_stage(info_items, dictionary)
    if reason or intro_item is None:
        result["reason"] = reason
        return result

    schedule = intro_item.get("schedule") or ""
    days, read = parse_days(schedule)
    result.update(schedule=schedule, scheduleDays=days, scheduleRead=read)
    start = created_date(course)
    if start is None:
        result["reason"] = NO_DATE
        return result
    result["createdDate"] = start.isoformat()
    split = split_days([has_photo(place) for _, place in valid], days)
    if split is None:
        result["reason"] = SPLIT_FAIL
        return result
    actual_days, ends = split
    result["days"] = actual_days
    result["reducedFrom"] = days if actual_days < days else None
    begin = 0
    for day, end in enumerate(ends, start=1):
        visit_date = (start + timedelta(days=day - 1)).isoformat()
        for order, ((item, place), time) in enumerate(zip(valid[begin:end], visit_times(end - begin)), start=1):
            entry = {
                "subnum": parse_subnum(item),
                "contentId": str(item.get("subcontentid")), "name": item.get("subname") or place.get("title"),
                "overview": item.get("subdetailoverview"), "day": day, "order": order,
                "visitDate": visit_date, "visitTime": time, "hasPhoto": has_photo(place),
                "contentTypeId": place.get("contenttypeid"),
            }
            entry.update({field: place.get(field) for field in PLACE_FIELDS})
            result["places"].append(entry)
        begin = end
    result["result"] = "PASS"
    return result
