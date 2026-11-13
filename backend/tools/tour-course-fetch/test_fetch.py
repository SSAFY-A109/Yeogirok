"""받기·멈춤·이어 받기·키 보호·보고(명세 3-3~3-6·3-9, AC-01~03·08~10). 가짜 HTTP 서버를 쓰고 실제 투어API 는 부르지 않는다."""

import json
import os
import shutil
import tempfile
import threading
import unittest
import urllib.parse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

import tour_fetch

KEY = "fake+Key/=="  # 인코딩되면 모양이 바뀌는 문자를 넣는다


def ok(items, total):
    return {"response": {"header": {"resultCode": "0000", "resultMsg": "OK"},
                         "body": {"items": {"item": items} if items else "", "numOfRows": len(items),
                                  "pageNo": 1, "totalCount": total}}}


def place(content_id, type_id="12", photo=True, mapx="127.0"):
    return {"contentid": content_id, "contenttypeid": type_id, "title": f"장소{content_id}", "mapx": mapx,
            "mapy": "37.5", "firstimage": "https://example.test/p.jpg" if photo else "",
            "cpyrhtDivCd": "Type1" if photo else "", "lDongRegnCd": "12", "lDongSignguCd": "110",
            "lclsSystm1": "VE", "lclsSystm2": "VE01", "lclsSystm3": "VE010100", "addr1": "가짜", "addr2": "",
            "createdtime": "20220304101010"}


class FakeTourApi:
    """경로·쿼리로 응답을 고르는 가짜 서버. 요청 수와 받은 키를 기록한다."""

    def __init__(self, dictionary, courses, intros, dict_rows=3):
        self.dictionary = dictionary
        self.courses = courses  # contentId → 장소 목록 항목들
        self.intros = intros
        self.dict_rows = dict_rows
        self.requests = []
        self.override = None  # (조건 함수, 상태 코드, 본문) — 맞으면 그 응답
        self.lock = threading.Lock()
        fake = self

        class Handler(BaseHTTPRequestHandler):
            def do_GET(self):
                path, _, query = self.path.partition("?")
                params = dict(urllib.parse.parse_qsl(query))
                with fake.lock:
                    fake.requests.append((path.rsplit("/", 1)[-1], params))
                status, body = fake.respond(path.rsplit("/", 1)[-1], params)
                data = body.encode() if isinstance(body, str) else json.dumps(body).encode()
                self.send_response(status)
                self.end_headers()
                self.wfile.write(data)

            def log_message(self, *args):
                pass

        self.server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        threading.Thread(target=self.server.serve_forever, daemon=True).start()
        self.base_url = f"http://127.0.0.1:{self.server.server_address[1]}"

    def respond(self, api, params):
        if self.override and self.override[0](api, params):
            return self.override[1], self.override[2]
        page = int(params.get("pageNo", 1))
        rows = int(params.get("numOfRows", 10))
        if api == "areaBasedList2":
            return 200, ok(self.dictionary[(page - 1) * rows: page * rows], len(self.dictionary))
        if api == "detailInfo2":
            items = self.courses.get(params["contentId"], [])
            return 200, ok(items[(page - 1) * rows: page * rows], len(items))
        if api == "detailIntro2":
            return 200, ok([{"contentid": params["contentId"], "schedule": self.intros.get(params["contentId"], "당일")}], 1)
        return 404, "no"

    def count(self, api=None):
        return sum(1 for a, _ in self.requests if api is None or a == api)

    def close(self):
        self.server.shutdown()
        self.server.server_close()


def sub(*ids):
    return [{"subnum": str(i), "subcontentid": cid, "subname": f"이름{cid}", "subdetailoverview": "설명"}
            for i, cid in enumerate(ids)]


class FetchTest(unittest.TestCase):

    def setUp(self):
        self.data_dir = tempfile.mkdtemp()
        os.environ["TOUR_API_KEY"] = KEY
        places = [place(str(i)) for i in range(1, 8)] + [place("8", photo=False), place("9", photo=False),
                                                         place("10", photo=False)]
        courses = [place("c1", "25"), place("c2", "25"), place("c3", "25"), place("c0", "25", mapx="0")]
        self.fake = FakeTourApi(places + courses, {
            "c1": sub("1", "2", "3"),               # 통과
            "c2": sub("8", "9", "10"),              # 사진 없음
            "c3": sub("4", "5", "6", "404"),        # 통과(사전에 없는 404 제외)
        }, {"c1": "당일", "c3": "1박2일"})
        self.logs = []

    def tearDown(self):
        self.fake.close()
        shutil.rmtree(self.data_dir, ignore_errors=True)
        os.environ.pop("TOUR_API_KEY", None)

    def run_fetch(self, *extra):
        return tour_fetch.main(["fetch", "--data-dir", self.data_dir, "--base-url", self.fake.base_url,
                                "--dict-rows", "3", "--interval", "0", "--retry-wait", "0", *extra], self.logs.append)

    def report(self):
        with open(os.path.join(self.data_dir, "report.json"), encoding="utf-8") as f:
            return json.load(f)

    def last_run(self):
        runs = sorted(os.listdir(os.path.join(self.data_dir, "runs")))
        with open(os.path.join(self.data_dir, "runs", runs[-1]), encoding="utf-8") as f:
            return json.load(f)

    def test_full_run_dictionary_check_and_report(self):
        self.assertEqual(self.run_fetch("--max-calls", "100"), 0)

        self.assertEqual(self.fake.count("areaBasedList2"), 5)  # 14건 / 3건씩
        report = self.report()
        self.assertEqual(report["dictionary"], {"totalCount": 14, "count": 14, "duplicates": 0, "pages": 5,
                                                "complete": True})
        self.assertEqual((report["processed"], report["passTotal"], report["firstStagePassed"]), (3, 2, 2))
        self.assertEqual(report["reasons"], {"NO_PHOTO": 1})
        self.assertEqual(report["remainingCourses"], 0)  # 좌표 0 인 c0 은 코스 목록에 없다
        self.assertEqual(report["province12Passed"], 2)
        self.assertEqual(report["daysOfPassed"], {"당일": 1, "1박2일": 1})
        self.assertEqual(self.fake.count("detailIntro2"), 2)  # 1차 탈락한 c2 는 일정을 부르지 않는다
        self.assertEqual(self.last_run()["stopReason"], "ALL_DONE")
        self.assertTrue(any("누적 통과 2개" in line for line in self.logs))

    def test_ac01_stops_at_max_calls_and_retries_count(self):
        # 첫 호출은 500, 재시도 성공 → 2번으로 센다.
        first = {"done": False}

        def fail_once(api, params):
            if api == "areaBasedList2" and not first["done"]:
                first["done"] = True
                return True
            return False

        self.fake.override = (fail_once, 500, "error")
        self.assertEqual(self.run_fetch("--max-calls", "3"), 0)

        self.assertEqual(self.fake.count(), 3)
        run = self.last_run()
        self.assertEqual((run["stopReason"], run["calls"]), ("MAX_CALLS", 3))
        self.assertEqual(len(os.listdir(os.path.join(self.data_dir, "dict"))), 2)  # 받은 페이지는 저장됨

    def test_ac02_resume_does_not_call_again(self):
        self.run_fetch("--max-calls", "7")  # 사전 5 + c? 일부
        before = self.fake.count()
        self.run_fetch("--max-calls", "100")
        second = self.fake.requests[before:]

        self.assertFalse(any(api == "areaBasedList2" for api, _ in second))
        info_calls = [(p["contentId"], p["pageNo"]) for api, p in second if api == "detailInfo2"]
        self.assertEqual(len(info_calls), len(set(info_calls)))
        self.run_fetch("--max-calls", "100")
        self.assertEqual(self.fake.count(), before + len(second))  # 세 번째 실행은 호출 0번
        self.assertEqual(self.report()["processed"], 3)

    def test_ac03_more_than_one_page_of_places(self):
        many = [place(str(100 + i)) for i in range(230)]
        self.fake.dictionary = many + [place("big", "25")]
        self.fake.courses = {"big": sub(*[str(100 + i) for i in range(230)])}
        self.run_fetch("--max-calls", "200", "--dict-rows", "100")

        info_pages = [p["pageNo"] for api, p in self.fake.requests if api == "detailInfo2"]
        self.assertEqual(info_pages, ["1", "2", "3"])
        with open(os.path.join(self.data_dir, "judged", "big.json"), encoding="utf-8") as f:
            judged = json.load(f)
        self.assertEqual((judged["infoTotal"], judged["infoReceived"], len(judged["places"])), (230, 230, 230))

    def test_max_courses_and_target_pass(self):
        self.run_fetch("--max-calls", "100", "--max-courses", "1")
        self.assertEqual((self.last_run()["stopReason"], self.report()["processed"]), ("MAX_COURSES", 1))
        self.run_fetch("--max-calls", "100", "--target-pass", "1")
        self.assertIn(self.last_run()["stopReason"], ("TARGET_PASS",))
        self.assertGreaterEqual(self.report()["passTotal"], 1)

    def test_same_seed_same_order(self):
        courses = [{"contentid": str(i)} for i in range(20)]
        self.assertEqual(tour_fetch.course_order(courses, "s"), tour_fetch.course_order(list(reversed(courses)), "s"))
        self.assertNotEqual([c["contentid"] for c in tour_fetch.course_order(courses, "s")],
                            [c["contentid"] for c in courses])

    def test_ac08_stop_immediately_without_retry(self):
        xml = f"<OpenAPI_ServiceResponse><returnAuthMsg>LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDS_ERROR</returnAuthMsg>" \
              f"<key>{KEY}</key><encoded>{urllib.parse.quote(KEY, safe='')}</encoded></OpenAPI_ServiceResponse>"
        result_code = {"response": {"header": {"resultCode": "30", "resultMsg": "SERVICE_KEY_IS_NOT_REGISTERED_ERROR"}}}
        for status, body, kind in [(200, xml, "NOT_JSON"), (401, "unauthorized", "HTTP_STATUS"),
                                   (403, "forbidden", "HTTP_STATUS"), (429, "too many", "HTTP_STATUS"),
                                   (200, result_code, "RESULT_CODE")]:
            with self.subTest(status=status, kind=kind):
                shutil.rmtree(self.data_dir, ignore_errors=True)
                start = self.fake.count()
                self.fake.override = (lambda api, params: True, status, body)
                self.assertEqual(self.run_fetch("--max-calls", "50"), 0)
                self.assertEqual(self.fake.count() - start, 1)  # 재시도 없음
                run = self.last_run()
                self.assertEqual((run["stopReason"], run["stopDetail"]["kind"], run["stopDetail"]["status"]),
                                 ("STOPPED_BY_RESPONSE", kind, status))

    def test_ac08_limit_body_is_recorded_with_key_masked(self):
        xml = f"<returnAuthMsg>LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDS_ERROR</returnAuthMsg><k>{KEY}</k>" \
              f"<e>{urllib.parse.quote(KEY, safe='')}</e>"
        self.fake.override = (lambda api, params: True, 200, xml)
        self.run_fetch("--max-calls", "50")
        body = self.last_run()["stopDetail"]["body"]
        self.assertIn("LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDS_ERROR", body)
        self.assertEqual(body.count("***"), 2)

    def test_ac09_key_never_saved_or_printed(self):
        self.fake.override = None
        self.run_fetch("--max-calls", "100")
        self.assertTrue(any(p.get("serviceKey") == KEY for _, p in self.fake.requests))  # 서버에는 보냈다
        secrets = {KEY, urllib.parse.quote(KEY, safe=""), urllib.parse.quote_plus(KEY)}
        for root, _, files in os.walk(self.data_dir):
            for name in files:
                with open(os.path.join(root, name), encoding="utf-8") as f:
                    text = f.read()
                for secret in secrets:
                    self.assertNotIn(secret, text, name)
        for secret in secrets:
            self.assertFalse(any(secret in line for line in self.logs))

    def test_no_key_no_calls(self):
        os.environ.pop("TOUR_API_KEY")
        self.assertEqual(self.run_fetch("--max-calls", "100"), 1)
        self.assertEqual(self.fake.count(), 0)

    def test_judge_command_reuses_files_without_calls(self):
        self.run_fetch("--max-calls", "100")
        calls = self.fake.count()
        self.assertEqual(tour_fetch.main(["judge", "--data-dir", self.data_dir, "--dict-rows", "3"], self.logs.append), 0)
        self.assertEqual(self.fake.count(), calls)
        self.assertEqual(self.report()["passTotal"], 2)
        self.assertEqual(tour_fetch.main(["report", "--data-dir", self.data_dir], self.logs.append), 0)


if __name__ == "__main__":
    unittest.main()
