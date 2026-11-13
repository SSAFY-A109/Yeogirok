"""주소 응답 테스트 (S15P31A109-95). OCR 모델을 불러오지 않는다. 상호·주소는 모두 가상 값.
실행: python test_address_response.py   (fastapi, httpx, python-multipart 필요)
- final_rules: 도로명 혼동 문자 보정, 건물번호까지 반환, 주소 오류가 상호명·날짜·시간을 막지 않음
- API: 6키 응답과 status (정상·주소 일부·주소 없음·FAILED), 주소 오류 때도 200, JPEG·PNG·WEBP 입력
"""
import cv2
import numpy as np
from fastapi.testclient import TestClient

import app as server
import final_rules
import receipt_parser
from address_parser import AddressPolicy
from final_rules import apply_final_rules, final_address, fix_road_lookalikes
from ocr_response import KEYS
from receipt_parser import parse_receipt

DT = "거래일시 2026-10-20 12:30"
ROAD = AddressPolicy(return_level="road")


def L(*t):
    return [(x, 0.95) for x in t]


def final(lines):
    return apply_final_rules(parse_receipt(lines).to_dict(), lines)


def _boom(*a, **k):
    raise RuntimeError("주소 처리 오류 (가짜)")


# ---------- final_rules ----------

def test_road_lookalike_fix():
    assert fix_road_lookalikes("서울시 강남구 가나로85일 25") == "서울시 강남구 가나로85길 25"
    assert fix_road_lookalikes("가나로 85일 25") == "가나로 85일 25"        # '로' 바로 뒤 숫자만
    assert fix_road_lookalikes("가나로85일") == "가나로85일"                # 뒤에 숫자가 없으면 그대로
    assert fix_road_lookalikes(DT) == DT


def test_road_lookalike_in_address():
    lines = L("가나식당", "서울시 강남구 가나로85일 25", DT)
    assert final_address(lines) == ("서울시 강남구 가나로85길 25", True)
    assert final_address(lines, ROAD) == ("서울시 강남구 가나로85길", True)


def test_building_number_included():
    d = final(L("가나식당", "서울시 노원구 동일로 123", DT))
    assert (d["address"], d["addressComplete"]) == ("서울시 노원구 동일로 123", True)
    assert final_address(L("서울시 노원구 노원로15길 10")) == ("서울시 노원구 노원로15길 10", True)


def test_road_only_jibun_none():
    assert final_address(L("가나식당", "서울시 노원구 동일로", DT)) == ("서울시 노원구 동일로", False)
    assert final_address(L("가나식당", "서울시 노원구 공릉동 123-4", DT)) == ("서울시 노원구", False)
    assert final_address(L("가나식당", DT)) == (None, False)


def test_road_level_same_as_before():                     # OCR_ADDRESS_LEVEL=road → -94 (receipt_parser) 와 같은 값
    for lines in (L("가나식당", "서울시 노원구 노원로15길 10", DT), L("가나식당", "서울시 노원구 동일로", DT), L(DT)):
        assert final_address(lines, ROAD) == receipt_parser.extract_address(lines)


def test_address_error_keeps_other_fields():
    lines = L("가나식당", "서울시 노원구 동일로 123", DT)
    ok = final(lines)
    old = final_rules.extract_address
    final_rules.extract_address = _boom
    try:
        bad = final(lines)
    finally:
        final_rules.extract_address = old
    assert (bad["address"], bad["addressComplete"]) == (None, False)
    for k in ("placeName", "date", "time"):
        assert bad[k] == ok[k] and ok[k] is not None, k


# ---------- API (가짜 OCR) ----------

NAME, DATE = ("가나식당", 10), (DT, 50)


def fake_runner(rows):
    def run(img):
        return ([t for t, _ in rows], [0.95] * len(rows), [[[0, y], [300, y], [300, y + 10], [0, y + 10]] for _, y in rows])
    return run


def post(rows, ext=".png", ctype="image/png"):
    old = server.get_runner
    server.get_runner = lambda: fake_runner(rows)
    try:
        ok, buf = cv2.imencode(ext, np.full((60, 40, 3), 255, np.uint8))
        assert ok, ext
        r = TestClient(server.app).post("/ocr/receipt", files={"file": ("r" + ext, buf.tobytes(), ctype)})
    finally:
        server.get_runner = old
    assert r.status_code == 200, (ext, r.status_code)
    j = r.json()
    assert tuple(j) == KEYS
    return j


def test_api_success_full_address():
    j = post([NAME, ("서울시 노원구 동일로 123", 30), DATE])
    assert j == {"status": "SUCCESS", "placeName": "가나식당", "date": "2026-10-20", "time": "12:30",
                 "address": "서울시 노원구 동일로 123", "addressComplete": True}


def test_api_partial_road_only():
    j = post([NAME, ("서울시 노원구 동일로", 30), DATE])
    assert (j["status"], j["address"], j["addressComplete"]) == ("PARTIAL", "서울시 노원구 동일로", False)


def test_api_partial_no_address():
    j = post([NAME, DATE])
    assert (j["status"], j["address"], j["addressComplete"]) == ("PARTIAL", None, False)
    assert (j["placeName"], j["date"], j["time"]) == ("가나식당", "2026-10-20", "12:30")


def test_api_failed():
    assert post([]) == {"status": "FAILED", "placeName": None, "date": None, "time": None,
                        "address": None, "addressComplete": False}


def test_api_address_error_still_200():
    old = final_rules.extract_address, receipt_parser.extract_address
    final_rules.extract_address = receipt_parser.extract_address = _boom
    try:
        j = post([NAME, ("서울시 노원구 동일로 123", 30), DATE])
    finally:
        final_rules.extract_address, receipt_parser.extract_address = old
    assert (j["status"], j["address"], j["addressComplete"]) == ("PARTIAL", None, False)
    assert (j["placeName"], j["date"], j["time"]) == ("가나식당", "2026-10-20", "12:30")


def test_api_accepts_jpeg_png_webp():                     # 백엔드가 받는 형식 (S15P31A109-70)
    for ext, ctype in ((".jpg", "image/jpeg"), (".png", "image/png"), (".webp", "image/webp")):
        assert post([NAME, ("서울시 노원구 동일로 123", 30), DATE], ext, ctype)["status"] == "SUCCESS", ext


if __name__ == "__main__":
    tests = [v for k, v in dict(globals()).items() if k.startswith("test_")]
    for t in tests:
        t()
        print("PASS", t.__name__)
    print(f"{len(tests)}/{len(tests)} 통과")
