from ocr_response import to_response


def test_success():
    r = to_response({"placeName": "가나식당", "date": "2026-10-20", "time": "12:30",
                     "address": "서울", "rawLines": ["카드번호 1234"], "confidence": "high"})
    assert r == {"status": "SUCCESS", "placeName": "가나식당", "date": "2026-10-20", "time": "12:30"}


def test_partial_missing_time():
    r = to_response({"placeName": "가나박물관", "date": "2026-10-20", "time": None})
    assert r == {"status": "PARTIAL", "placeName": "가나박물관", "date": "2026-10-20", "time": None}


def test_failed_and_empty_string():
    r = to_response({"placeName": "", "date": None, "time": None})
    assert r == {"status": "FAILED", "placeName": None, "date": None, "time": None}


def test_no_raw_text_in_response():
    r = to_response({"placeName": "가", "date": None, "time": None, "rawLines": ["승인번호 999"]})
    assert set(r) == {"status", "placeName", "date", "time"}


if __name__ == "__main__":
    tests = [v for k, v in dict(globals()).items() if k.startswith("test_")]
    for t in tests:
        t()
        print("PASS", t.__name__)
    print(f"{len(tests)}/{len(tests)} 통과")
