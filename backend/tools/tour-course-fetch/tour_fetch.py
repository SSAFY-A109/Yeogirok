"""투어API 여행코스 받아 오기·판정(Jira S15P31A109-218, 명세 .handoff/specs/tour-course-seed.md 3절).

사용법은 README.md. 키는 환경 변수 TOUR_API_KEY 로만 받는다.
"""

import argparse
import hashlib
import math
import os
import sys
from collections import Counter
from datetime import datetime
from pathlib import Path

import tour_bundle
import tour_judge as judge
from tour_api import DEFAULT_BASE_URL, BudgetExhausted, StopRun, TourApi, items_of, total_of
from tour_store import Store

COURSE_TYPE = "25"
DEFAULT_SEED = "yeogirok-218"
DICT_ROWS = 1000
INFO_ROWS = 100


def default_data_dir():
    return str(Path.home() / "yeogirok-tour-data")


def now():
    return datetime.now().strftime("%Y-%m-%dT%H:%M:%S")


def course_order(courses, seed):
    """sha256("{seed}:{contentId}") 오름차순. 같은 seed 면 언제 실행해도 같은 순서다."""
    return sorted(courses, key=lambda c: hashlib.sha256(f"{seed}:{c['contentid']}".encode()).hexdigest())


class Fetcher:
    def __init__(self, store, api, dict_rows=DICT_ROWS, info_rows=INFO_ROWS, log=print):
        self.store = store
        self.api = api
        self.dict_rows = dict_rows
        self.info_rows = info_rows
        self.log = log

    # 장소 사전
    def ensure_dictionary(self):
        first = self.store.dict_page(1)
        if first is None:
            first = self.api.get("areaBasedList2", numOfRows=self.dict_rows, pageNo=1, arrange="A")
            self.store.save_dict_page(1, first)
        total = total_of(first)
        pages = max(1, math.ceil(total / self.dict_rows))
        for page in range(2, pages + 1):
            if self.store.dict_page(page) is None:
                self.store.save_dict_page(page, self.api.get(
                    "areaBasedList2", numOfRows=self.dict_rows, pageNo=page, arrange="A"))
        return self.write_dictionary_meta(total, pages)

    def write_dictionary_meta(self, total, pages):
        items = [item for page in range(1, pages + 1) for item in items_of(self.store.dict_page(page))]
        ids = [str(item.get("contentid")) for item in items]
        meta = {"totalCount": total, "numOfRows": self.dict_rows, "pages": pages, "count": len(items),
                "duplicates": len(ids) - len(set(ids)), "complete": True, "checkedAt": now()}
        if meta["count"] != total or meta["duplicates"]:
            self.log(f"[경고] 장소 사전 건수 {meta['count']} / totalCount {total}, 중복 {meta['duplicates']}건")
        self.store.write(meta, "dict", "meta.json")
        return meta

    def dictionary(self):
        meta = self.store.read("dict", "meta.json")
        if not meta or not meta.get("complete"):
            return None
        result = {}
        for page in range(1, meta["pages"] + 1):
            for item in items_of(self.store.dict_page(page)):
                result.setdefault(str(item.get("contentid")), item)
        return result

    @staticmethod
    def courses(dictionary, seed):
        courses = [item for item in dictionary.values()
                   if str(item.get("contenttypeid")) == COURSE_TYPE and judge.has_coordinates(item)]
        return course_order(courses, seed)

    # 코스 하나
    def process(self, course, dictionary, state):
        content_id = str(course["contentid"])
        items, total = self.fetch_info(content_id, state)
        result = judge.judge(course, items, total, None, dictionary)
        if result["reason"] is None:
            self.mark(state, content_id, "intro")
            intro = self.store.intro(content_id)
            if intro is None:
                intro = self.api.get("detailIntro2", contentId=content_id, contentTypeId=COURSE_TYPE)
                self.store.save_intro(content_id, intro)
            result = judge.judge(course, items, total, (items_of(intro) or [{}])[0], dictionary)
        self.store.save_judged(result)
        self.mark(state, content_id, "done")
        return result

    def fetch_info(self, content_id, state):
        self.mark(state, content_id, "info")
        first = self.store.info_page(content_id, 1)
        if first is None:
            first = self.api.get("detailInfo2", contentId=content_id, contentTypeId=COURSE_TYPE,
                                 numOfRows=self.info_rows, pageNo=1)
            self.store.save_info_page(content_id, 1, first)
        total = total_of(first)
        for page in range(2, max(1, math.ceil(total / self.info_rows)) + 1):
            if self.store.info_page(content_id, page) is None:
                self.store.save_info_page(content_id, page, self.api.get(
                    "detailInfo2", contentId=content_id, contentTypeId=COURSE_TYPE,
                    numOfRows=self.info_rows, pageNo=page))
        return self.stored_info(content_id, total), total

    def stored_info(self, content_id, total):
        pages = max(1, math.ceil(total / self.info_rows))
        items = [item for page in range(1, pages + 1) for item in items_of(self.store.info_page(content_id, page))]
        if len(items) != total:
            self.log(f"[경고] 코스 {content_id} 장소 {len(items)}건 / totalCount {total}")
        return items

    def mark(self, state, content_id, stage):
        state["courses"][content_id] = {"stage": stage, "updatedAt": now()}
        self.store.save_state(state)


# 보고
def build_report(store, new_pass_ids):
    judged = store.all_judged()
    meta = store.read("dict", "meta.json") or {}
    state = store.state()
    passed = [j for j in judged if j["result"] == "PASS"]
    first_passed = [j for j in judged if j["reason"] not in judge.FIRST_STAGE_REASONS]
    with_intro = [j for j in judged if j["schedule"] is not None]
    total_courses = None
    if meta.get("complete"):
        fetcher = Fetcher(store, None)
        total_courses = len(fetcher.courses(fetcher.dictionary(), state.get("seed") or DEFAULT_SEED))

    def days_bucket(days):
        return "당일" if days == 1 else ("1박2일" if days == 2 else "2박3일 이상")

    by_id = {j["contentId"]: j for j in judged}
    return {
        "newPass": [{"contentId": cid, "days": by_id[cid]["days"], "places": len(by_id[cid]["places"]),
                     "photoPlaces": sum(p["hasPhoto"] for p in by_id[cid]["places"])}
                    for cid in new_pass_ids if cid in by_id],
        "passTotal": len(passed),
        "processed": len(judged),
        "firstStagePassed": len(first_passed),
        "secondStagePassed": len(passed),
        "passRate": round(len(passed) / len(judged), 3) if judged else None,
        "reasons": dict(Counter(j["reason"] for j in judged if j["reason"])),
        "daysOfPassed": dict(Counter(days_bucket(j["days"]) for j in passed)),
        "reducedPassed": sum(1 for j in passed if j["reducedFrom"]),
        "schedules": dict(Counter((j["schedule"] or "(빈 값)") for j in with_intro)),
        "unreadSchedules": sum(1 for j in with_intro if not j["scheduleRead"]),
        "photoCopyright": dict(Counter((p.get("cpyrhtDivCd") or "(빈 값)")
                                       for j in passed for p in j["places"] if p["hasPhoto"])),
        "provincesOfPassed": dict(Counter(j["provinceCode"] or "(없음)" for j in passed)),
        "province12Passed": sum(1 for j in passed if j["provinceCode"] == "12"),
        "callsTotal": state.get("calls", 0),
        "remainingCourses": None if total_courses is None else total_courses - len(judged),
        "dictionary": {k: meta.get(k) for k in ("totalCount", "count", "duplicates", "pages", "complete")},
    }


REASON_NAMES = {judge.FEW_VALID: "유효 장소 3곳 미만", judge.NO_PHOTO: "사진 없음", judge.SPLIT_FAIL: "배분 불가",
                judge.EMPTY_INFO: "빈 응답", judge.NO_DATE: "등록일 없음"}


def print_report(report, log=print):
    log("── 판정 보고 ──")
    log(f"이번 실행 새로 통과 {len(report['newPass'])}개 / 누적 통과 {report['passTotal']}개")
    for c in report["newPass"]:
        log(f"  {c['contentId']}  {c['days']}일  장소 {c['places']}  사진 장소 {c['photoPlaces']}")
    log(f"처리 {report['processed']}개 · 1차 통과 {report['firstStagePassed']} · 2차 통과 {report['secondStagePassed']}"
        f" · 통과 비율 {report['passRate']}")
    log("탈락 사유: " + (", ".join(f"{REASON_NAMES.get(k, k)} {v}" for k, v in report["reasons"].items()) or "없음"))
    log(f"일수별 통과: {report['daysOfPassed']} · 일수를 줄여 통과 {report['reducedPassed']}")
    log(f"schedule 값: {report['schedules']} · 읽지 못함 {report['unreadSchedules']}")
    log(f"사진 저작권 유형(통과 코스): {report['photoCopyright']}")
    log(f"시·도별 통과(lDongRegnCd): {report['provincesOfPassed']} · 코드 12: {report['province12Passed']}")
    log(f"누적 호출 {report['callsTotal']}번 · 남은 코스 {report['remainingCourses']}")
    log(f"장소 사전: {report['dictionary']}")


# 명령
def cmd_fetch(args, log=print):
    key = os.environ.get("TOUR_API_KEY")
    if not key:
        log("TOUR_API_KEY 환경 변수가 없어 호출하지 않고 끝냅니다.")
        return 1
    store = Store(args.data_dir)
    state = store.state()
    if state.get("seed") and state["seed"] != args.seed:
        log(f"[주의] 이전 실행 seed({state['seed']})와 달라 처리 순서가 바뀝니다.")
    state["seed"] = args.seed
    meta = store.read("dict", "meta.json")
    log(f"이번 실행 최대 {args.max_calls}번 호출(상한). 누적 {state.get('calls', 0)}번."
        + ("" if meta else f" 장소 사전 남은 페이지 약 {math.ceil(49670 / args.dict_rows) - len(store.dict_pages())}개 먼저."))
    api = TourApi(key, args.max_calls, base_url=args.base_url, interval=args.interval,
                  retry_waits=(args.retry_wait, args.retry_wait * 3), log=log)
    fetcher = Fetcher(store, api, args.dict_rows, args.info_rows, log)
    run = {"id": datetime.now().strftime("%Y%m%d-%H%M%S"), "startedAt": now(),
           "args": {k: v for k, v in vars(args).items() if k != "func"},
           "stopReason": None, "stopDetail": None, "dictionary": None, "processed": 0, "newPass": []}
    try:
        run["dictionary"] = meta or fetcher.ensure_dictionary()
        dictionary = fetcher.dictionary()
        courses = fetcher.courses(dictionary, args.seed)
        passed = sum(1 for j in store.all_judged() if j["result"] == "PASS")
        log(f"코스 {len(courses)}개(좌표 있음) 중 처리됨 {len(store.all_judged())}개, 누적 통과 {passed}개")
        run["stopReason"] = "ALL_DONE"
        for course in courses:
            if store.judged(str(course["contentid"])) is not None:
                continue
            if passed >= args.target_pass:
                run["stopReason"] = "TARGET_PASS"
                break
            if run["processed"] >= args.max_courses:
                run["stopReason"] = "MAX_COURSES"
                break
            result = fetcher.process(course, dictionary, state)
            run["processed"] += 1
            if result["result"] == "PASS":
                passed += 1
                run["newPass"].append(result["contentId"])
        else:
            if passed >= args.target_pass:
                run["stopReason"] = "TARGET_PASS"
    except BudgetExhausted:
        run["stopReason"] = "MAX_CALLS"
    except StopRun as e:
        run["stopReason"] = "STOPPED_BY_RESPONSE"
        run["stopDetail"] = e.to_dict()
        log(f"[멈춤] 응답 {e.kind} status={e.status} — 실행 기록 runs/{run['id']}.json 에 본문(키 가림)을 남겼습니다.")
    finally:
        state["calls"] = state.get("calls", 0) + api.calls
        store.save_state(state)
        run["calls"] = api.calls
        run["endedAt"] = now()
        store.save_run(run)
    log(f"멈춘 이유: {run['stopReason']} · 이번 호출 {api.calls}번")
    report = build_report(store, run["newPass"])
    store.write(report, "report.json")
    print_report(report, log)
    return 0


def cmd_judge(args, log=print):
    """저장된 원본으로 판정만 다시 한다(호출 0번)."""
    store = Store(args.data_dir)
    fetcher = Fetcher(store, None, args.dict_rows, args.info_rows, log)
    dictionary = fetcher.dictionary()
    if dictionary is None:
        log("장소 사전이 다 받아지지 않아 판정할 수 없습니다.")
        return 1
    rejudged = 0
    for course in fetcher.courses(dictionary, store.state().get("seed") or args.seed):
        content_id = str(course["contentid"])
        first = store.info_page(content_id, 1)
        if first is None:
            continue
        total = total_of(first)
        if len(store.info_pages(content_id)) < max(1, math.ceil(total / args.info_rows)):
            continue
        items = fetcher.stored_info(content_id, total)
        intro = store.intro(content_id)
        result = judge.judge(course, items, total, None if intro is None else (items_of(intro) or [{}])[0], dictionary)
        if result["reason"] is None and intro is None:
            # 1차를 새로 통과했지만 일정이 아직 없다 → 판정 파일을 두지 않아 다음 fetch 가 이어서 받는다.
            path = store.root / "judged" / f"{content_id}.json"
            if path.exists():
                path.unlink()
            continue
        store.save_judged(result)
        rejudged += 1
    log(f"판정 다시 함: {rejudged}개(호출 0번)")
    report = build_report(store, [])
    store.write(report, "report.json")
    print_report(report, log)
    return 0


def cmd_report(args, log=print):
    store = Store(args.data_dir)
    last = store.last_run()
    print_report(build_report(store, last["newPass"] if last else []), log)
    return 0


def cmd_bundle(args, log=print):
    return tour_bundle.cmd_bundle(args, log, Fetcher, Store(args.data_dir))


def parser():
    p = argparse.ArgumentParser(description="투어API 여행코스 받아 오기·판정(S15P31A109-218)")
    common = argparse.ArgumentParser(add_help=False)
    common.add_argument("--data-dir", default=default_data_dir())
    common.add_argument("--seed", default=DEFAULT_SEED)
    common.add_argument("--dict-rows", type=int, default=DICT_ROWS, help=argparse.SUPPRESS)
    common.add_argument("--info-rows", type=int, default=INFO_ROWS, help=argparse.SUPPRESS)
    sub = p.add_subparsers(dest="command", required=True)
    fetch = sub.add_parser("fetch", parents=[common], help="받기·판정")
    fetch.add_argument("--max-calls", type=int, required=True)
    fetch.add_argument("--max-courses", type=int, default=10 ** 9)
    fetch.add_argument("--target-pass", type=int, default=10 ** 9)
    fetch.add_argument("--base-url", default=DEFAULT_BASE_URL, help=argparse.SUPPRESS)
    fetch.add_argument("--interval", type=float, default=0.3, help=argparse.SUPPRESS)
    fetch.add_argument("--retry-wait", type=float, default=1.0, help=argparse.SUPPRESS)
    fetch.set_defaults(func=cmd_fetch)
    bundle = sub.add_parser("bundle", parents=[common], help="통과 코스로 묶음 파일 만들기(222)")
    bundle.add_argument("--ids", help="넣을 contentId 목록(쉼표)")
    bundle.add_argument("--count", type=int, help="처리 순서대로 앞 묶음에 없는 통과 코스 개수")
    bundle.add_argument("--bundle-id", help="기본 오늘날짜-순번(예 20261114-01)")
    bundle.add_argument("--max-calls", type=int, default=100, help="소개 글(detailCommon2) 투어API 호출 상한")
    bundle.add_argument("--base-url", default=DEFAULT_BASE_URL, help=argparse.SUPPRESS)
    bundle.add_argument("--kakao-base-url", default=tour_bundle.DEFAULT_KAKAO_BASE_URL, help=argparse.SUPPRESS)
    bundle.add_argument("--interval", type=float, default=0.3, help=argparse.SUPPRESS)
    bundle.add_argument("--retry-wait", type=float, default=1.0, help=argparse.SUPPRESS)
    bundle.set_defaults(func=cmd_bundle)
    sub.add_parser("judge", parents=[common], help="판정만 다시(호출 0번)").set_defaults(func=cmd_judge)
    sub.add_parser("report", parents=[common], help="보고만 다시 출력").set_defaults(func=cmd_report)
    return p


def main(argv=None, log=print):
    args = parser().parse_args(argv)
    return args.func(args, log)


if __name__ == "__main__":
    sys.exit(main())
