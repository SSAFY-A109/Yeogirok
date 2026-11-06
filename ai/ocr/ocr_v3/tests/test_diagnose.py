"""진단 명령: 방향 판정·원문 저장·필드 추적·가림 처리 (가짜 paddleocr, 가상 문자열, GPU 불필요)."""
import io
import json
import os
import sys
import tempfile
import types

import numpy as np
from PIL import Image

from ocrlab import diagnose
from ocrlab.settings import load_config


FAIL_CPU = [False]


def _fake():
    m = types.ModuleType("paddleocr")
    m.__version__ = "3.7.0"

    class PaddleOCR:
        def __init__(self, **kw):
            self.kw = kw

        def predict(self, img):
            if self.kw.get("device") == "cpu" and FAIL_CPU[0]:
                raise NotImplementedError("(Unimplemented) ConvertPirAttribute2RuntimeAttribute not support")
            T = ["(주)가나유통(가나할인마트", "노원점)", "대표자:홍길동 220-85-12345", "주 소:서울 노원구 동일로 120 1층",
                 "판매일:26-1C-01", "18:26,목요일", "TEL 02-123-4567"]
            Y = [100, 80, 160, 200, 300, 302, 360]
            X = [10, 300, 10, 10, 10, 200, 10]
            P = [np.array([[x, y], [x + 250, y], [x + 250, y + 20], [x, y + 20]]) for x, y in zip(X, Y)]
            return [{"rec_texts": T, "rec_scores": [0.9] * len(T), "rec_polys": P, "dt_polys": P,
                     "text_det_params": {"thresh": 0.2, "box_thresh": 0.4}}]
    m.PaddleOCR = PaddleOCR
    return m


def test_diagnose_end_to_end():
    d = tempfile.mkdtemp()
    im = Image.new("RGB", (600, 400), "white")
    im.paste((0, 0, 0), (0, 0, 100, 400))
    ex = Image.Exif()
    ex[274] = 6
    p = os.path.join(d, "a.jpg")
    im.save(p, exif=ex.tobytes())
    old = sys.modules.get("paddleocr")
    sys.modules["paddleocr"] = _fake()
    try:
        cfg = load_config(sets=["ocr.device=cpu"])
        out = os.path.join(d, "diag")
        r = diagnose.run(p, out, cfg, also_cpu=True, expect={"date": "2026-10-01", "time": "18:26"})
        try:
            diagnose.run(p, out, cfg)                      # 같은 폴더에 다시 쓰지 않음
            raise AssertionError("덮어씀")
        except ValueError:
            pass
    finally:
        if old is None:
            sys.modules.pop("paddleocr", None)
        else:
            sys.modules["paddleocr"] = old
    files = set(os.listdir(out))
    assert {"ocr_input.png", "ocr_raw.json", "lines.json", "trace.json", "gpu.json", "orientation.json", "report.md",
            "ocr_boxes.jpg", "cpu_compare.json"} <= files
    ori = json.load(open(os.path.join(out, "orientation.json"), encoding="utf-8"))
    assert ori["best_match_rotation"] == 0 and ori["decoded_hw"] == [600, 400]
    tr = json.load(open(os.path.join(out, "trace.json"), encoding="utf-8"))
    assert tr["placeName"]["chosen"] == "노원점)" and tr["placeName"]["rule"].startswith("라벨 없음 → 상단 12줄 중 지점명")
    assert all(r_["rule_check_consistent"] for r_ in tr["placeName"]["top12"])
    assert tr["datetime"]["near_miss_date_tokens"] == ["26-1C-01"] and tr["datetime"]["expected_date_digits_in_ocr"] is False
    assert tr["address"]["chosen"] == "서울 노원구 동일로" and tr["address"]["complete"] is True
    rep = open(os.path.join(out, "report.md"), encoding="utf-8").read()
    assert "홍길동" not in rep and "220-85-12345" not in rep and "02-123-4567" not in rep and "○○○" in rep
    assert r["response"]["time"] == "18:26" and r["response"]["date"] is None


def test_mask_text():
    m = diagnose.mask_text
    assert m("대 표 자:홍길동") == "대 표 자:○○○" and m("승인번호 30078319") == "승인번호 ********"
    assert m("카드번호 5570-42**-****-****") == "카드번호 ****" and "26-10-01" in m("판매일:26-10-01 18:26")


def test_cpu_failure_does_not_block_report():
    d = tempfile.mkdtemp()
    p = os.path.join(d, "b.jpg")
    Image.new("RGB", (300, 200), "white").save(p)
    old = sys.modules.get("paddleocr")
    sys.modules["paddleocr"] = _fake()
    FAIL_CPU[0] = True
    try:
        out = os.path.join(d, "diag")
        diagnose.run(p, out, load_config(sets=["ocr.device=gpu:0"]), also_cpu=True)
    finally:
        FAIL_CPU[0] = False
        if old is None:
            sys.modules.pop("paddleocr", None)
        else:
            sys.modules["paddleocr"] = old
    rep = open(os.path.join(out, "report.md"), encoding="utf-8").read()
    assert "CPU 비교 실패" in rep and os.path.exists(os.path.join(out, "gpu.json"))
    assert "error" in json.load(open(os.path.join(out, "cpu_compare.json"), encoding="utf-8"))


def test_mask_split_name_labels():
    m = diagnose.mask_text
    assert m("대 표 ○○ 자:홍길동 123") == "대 표 ○○ 자:○○○ 123"
    assert m("계산원:김가나(018)") == "계산원:○○○(018)" and m("판매원 : 이나라") == "판매원 : ○○○"
    assert m("서울 노원구 동일로 120") == "서울 노원구 동일로 120"
