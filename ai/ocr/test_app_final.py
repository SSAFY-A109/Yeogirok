"""최종 모델 운영 반영 테스트 (S15P31A109-49). OCR 모델을 불러오지 않는다. 영수증 값은 모두 가상.
실행: python test_app_final.py   (fastapi, httpx, python-multipart 필요)
- 후처리 v3 (final_rules): ocr_v3/tests 의 test_date_lookalike.py·test_name_v3.py 와 같은 입력·기대값
- 방향 보정 (orient_retry): ocr_v3/tests/test_orient.py 와 같은 가짜 OCR (왼쪽 위 검은 점 = 바른 방향)
"""
import os

import cv2
import numpy as np
from fastapi.testclient import TestClient

import app as server
from final_rules import apply_final_rules, fix_date_lookalikes, name_v3
from orient_retry import read_with_orientation
from receipt_parser import parse_receipt


def L(*t):
    return [(x, 0.95) for x in t]


def final(lines):
    return apply_final_rules(parse_receipt(lines).to_dict(), lines)


def test_date_lookalike_fixed_only_when_missing():
    assert fix_date_lookalikes("26-1C-01") == "26-10-01"
    d = final(L("가나식당", "판매일:26-1C-01 18:26,목요일"))
    assert (d["date"], d["time"]) == ("2026-10-01", "18:26")
    assert final(L("가나식당", "거래일시 2026-10-20 12:30", "유효 26-1C-01"))["date"] == "2026-10-20"


CARD = L("키스-체크 가맹점명/주소가 실제와 다른경우", "KIS정보통신 신고안내(포상금 10만원 지급) 여신금융협회(02-0000-0000)",
         "가나마스터 승인", "할부: [일시불]", "거래일시: 26/01/02 10:11:12", "금 액: 1,455원", "합 계: 1,600원",
         "승인번호(가나카드) 00001234", "가맹점/사업자번호 123456789/123-45-67890")


def test_name_v3_card_slip():
    lines = CARD + L("홍길동(TEL:0212345678) 가나의원", "서울특별시 강남구 가나대로 12 3층")
    assert name_v3(lines) == ("가나의원", "카드전표 가맹점 줄")
    assert final(lines)["placeName"] == "가나의원"


GOOD = [("가나식당", 10), ("서울시 노원구 동일로 123", 30), ("거래일시 2026-10-20 12:30", 50), ("합계 9,000", 70),
        ("카드 승인", 90), ("부가세 818", 110)]
BAD = [("ㅁ녀ㅕ ld", 10), ("##ㄷ 0z", 30), ("코EI', 7", 50)]


def marked(h=60, w=40):
    img = np.full((h, w, 3), 255, np.uint8)
    img[:6, :6] = 0
    return img


def corner(img):
    h, w = img.shape[:2]
    blk = {"TL": img[:6, :6], "TR": img[:6, w - 6:], "BL": img[h - 6:, :6], "BR": img[h - 6:, w - 6:]}
    return min(blk, key=lambda k: blk[k].mean())


class FakeRunner:
    def __init__(self):
        self.calls = 0

    def __call__(self, img):
        self.calls += 1
        rows = GOOD if corner(img) == "TL" else BAD
        return ([t for t, _ in rows], [0.95] * len(rows), [[[0, y], [100, y], [100, y + 10], [0, y + 10]] for _, y in rows])


def test_upright_photo_read_once():
    r = FakeRunner()
    texts, _, _, meta = read_with_orientation(r, marked())
    assert r.calls == 1 and meta["angle"] == 0 and texts == [t for t, _ in GOOD]


def test_upside_down_photo_rotated_and_stops_early():
    r = FakeRunner()
    texts, _, _, meta = read_with_orientation(r, cv2.rotate(marked(), cv2.ROTATE_180))
    assert meta["angle"] == 180 and texts == [t for t, _ in GOOD]
    assert r.calls == 2                                  # fast: 180도에서 충분히 읽혀 90·270도는 건너뜀


def test_orientation_off():
    r = FakeRunner()
    texts, _, _, meta = read_with_orientation(r, cv2.rotate(marked(), cv2.ROTATE_180), "off")
    assert r.calls == 1 and meta["angle"] == 0 and texts == [t for t, _ in BAD]


def test_api_keys_unchanged():
    old = server.get_runner
    server.get_runner = lambda: FakeRunner()
    try:
        ok, buf = cv2.imencode(".png", marked(600, 400))
        r = TestClient(server.app).post("/ocr/receipt", files={"file": ("r.png", buf.tobytes(), "image/png")})
    finally:
        server.get_runner = old
    assert r.status_code == 200
    assert set(r.json()) == {"status", "placeName", "date", "time"}


def test_rec_model_dir_default_official_and_missing():
    old = os.environ.get("OCR_REC_MODEL_DIR")
    try:
        os.environ.pop("OCR_REC_MODEL_DIR", None)
        assert server.rec_model_dir() == server.DEFAULT_REC_MODEL_DIR
        assert server.DEFAULT_REC_MODEL_DIR.replace(os.sep, "/").endswith("models/rec_b30_v1")
        os.environ["OCR_REC_MODEL_DIR"] = "official"
        assert server.rec_model_dir() is None
        os.environ["OCR_REC_MODEL_DIR"] = "/없는/폴더"
        try:
            server.rec_model_dir()
            raise AssertionError("없는 폴더인데 오류가 안 남")
        except RuntimeError:
            pass
    finally:
        if old is None:
            os.environ.pop("OCR_REC_MODEL_DIR", None)
        else:
            os.environ["OCR_REC_MODEL_DIR"] = old


if __name__ == "__main__":
    tests = [v for k, v in dict(globals()).items() if k.startswith("test_")]
    for t in tests:
        t()
        print("PASS", t.__name__)
    print(f"{len(tests)}/{len(tests)} 통과")
