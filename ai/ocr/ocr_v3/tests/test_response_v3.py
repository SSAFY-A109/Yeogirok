"""응답 규격 v3 테스트 (status 4개 기준, address·addressComplete). 값은 가상."""
from ocr_response import KEYS, to_response

FULL = {"placeName": "가나식당", "date": "2026-10-20", "time": "12:30"}


def R(**kw):
    return to_response({**FULL, **kw})


def test_success_only_with_core_and_complete_address():
    r = R(address="서울시 노원구 동일로", addressComplete=True, rawLines=["카드번호 1234"], confidence="high")
    assert r == {"status": "SUCCESS", "placeName": "가나식당", "date": "2026-10-20", "time": "12:30",
                 "address": "서울시 노원구 동일로", "addressComplete": True}
    assert tuple(r) == KEYS


def test_partial_when_address_incomplete():                 # 건의 예시 JSON 그대로
    r = R(address="서울시 노원구 동일로", addressComplete=False)
    assert r == {"status": "PARTIAL", "placeName": "가나식당", "date": "2026-10-20", "time": "12:30",
                 "address": "서울시 노원구 동일로", "addressComplete": False}


def test_partial_jibun_only():
    assert R(address="서울시 노원구", addressComplete=False)["status"] == "PARTIAL"


def test_no_address():
    r = R(address=None)
    assert r["status"] == "PARTIAL" and r["address"] is None and r["addressComplete"] is False


def test_complete_address_but_core_missing_is_not_success():
    for k in ("placeName", "date", "time"):
        r = R(**{k: None}, address="서울시 노원구 동일로", addressComplete=True)
        assert r["status"] == "PARTIAL" and r["addressComplete"] is True, k


def test_all_missing_failed():
    r = to_response({"placeName": "", "date": None, "time": None, "address": ""})
    assert r == {"status": "FAILED", "placeName": None, "date": None, "time": None,
                 "address": None, "addressComplete": False}
    assert to_response({})["status"] == "FAILED"


def test_address_only_is_partial():                          # 11/02 규격 문서: FAILED 는 4개 모두 없을 때만
    r = to_response({"address": "서울시 노원구 동일로", "addressComplete": True})
    assert r["status"] == "PARTIAL"


def test_address_complete_never_null_and_needs_address():
    r = R(address=None, addressComplete=True)
    assert r["addressComplete"] is False and r["status"] == "PARTIAL"
    assert R(address="서울시 노원구 동일로")["addressComplete"] is False      # 키가 없어도 false
    assert R(address="서울시 노원구 동일로", addressComplete=None)["addressComplete"] is False


def test_no_raw_text_or_debug_in_response():
    r = R(rawLines=["승인번호 999"], diagnostics={"x": 1}, address=None)
    assert tuple(r) == KEYS
