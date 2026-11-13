"""OCR 응답 형식 v3 테스트 (S15P31A109-95). 값은 모두 가상.
실행: python test_ocr_response.py
"""
from ocr_response import KEYS, to_response

FULL = {"placeName": "가나식당", "date": "2026-10-20", "time": "12:30"}
ADDR = "서울시 노원구 동일로 123"


def R(**kw):
    return to_response({**FULL, **kw})


def test_success():                                          # 상호명·날짜·시간 + 건물번호까지 읽은 주소
    r = R(address=ADDR, addressComplete=True, rawLines=["카드번호 1234"], confidence="high")
    assert r == {"status": "SUCCESS", "placeName": "가나식당", "date": "2026-10-20", "time": "12:30",
                 "address": ADDR, "addressComplete": True}
    assert tuple(r) == KEYS == ("status", "placeName", "date", "time", "address", "addressComplete")


def test_partial_missing_time():
    r = R(time=None, address=ADDR, addressComplete=True)
    assert r["status"] == "PARTIAL" and r["time"] is None and r["addressComplete"] is True


def test_partial_when_address_incomplete():                 # 도로명까지만 읽음
    r = R(address="서울시 노원구 동일로", addressComplete=False)
    assert r == {"status": "PARTIAL", "placeName": "가나식당", "date": "2026-10-20", "time": "12:30",
                 "address": "서울시 노원구 동일로", "addressComplete": False}


def test_partial_jibun_only():
    assert R(address="서울시 노원구", addressComplete=False)["status"] == "PARTIAL"


def test_partial_no_address():
    r = R(address=None)
    assert r["status"] == "PARTIAL" and r["address"] is None and r["addressComplete"] is False


def test_complete_address_but_core_missing_is_not_success():
    for k in ("placeName", "date", "time"):
        r = R(**{k: None}, address=ADDR, addressComplete=True)
        assert r["status"] == "PARTIAL" and r["addressComplete"] is True, k


def test_address_only_is_partial():                          # FAILED 는 4개 모두 없을 때만
    assert to_response({"address": ADDR, "addressComplete": True})["status"] == "PARTIAL"


def test_failed_and_empty_string():
    r = to_response({"placeName": "", "date": None, "time": None, "address": ""})
    assert r == {"status": "FAILED", "placeName": None, "date": None, "time": None,
                 "address": None, "addressComplete": False}
    assert to_response({})["status"] == "FAILED"


def test_address_complete_never_null_and_needs_address():
    r = R(address=None, addressComplete=True)
    assert r["addressComplete"] is False and r["status"] == "PARTIAL"
    assert R(address=ADDR)["addressComplete"] is False                      # 키가 없어도 false
    assert R(address=ADDR, addressComplete=None)["addressComplete"] is False


def test_no_raw_text_in_response():
    r = R(rawLines=["승인번호 999"], diagnostics={"x": 1}, visitedAt="2026-10-20T12:30:00+09:00", address=None)
    assert tuple(r) == KEYS


if __name__ == "__main__":
    tests = [v for k, v in dict(globals()).items() if k.startswith("test_")]
    for t in tests:
        t()
        print("PASS", t.__name__)
    print(f"{len(tests)}/{len(tests)} 통과")
