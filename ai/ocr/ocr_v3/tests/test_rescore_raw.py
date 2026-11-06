"""rescore_raw.py: 가짜 OCR 엔진으로 lab.py eval --save-raw 결과 폴더를 만든 뒤 다시 채점한다. 가상 영수증, GPU 없음."""
import argparse
import json
import os
import subprocess
import sys
import tempfile

import cv2
import numpy as np

from ocrlab import engine, evaluate

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TOKS = {   # 사진 너비 → OCR 결과 (텍스트, y)
    30: [("가나식당", 10), ("서울시 노원구 동일로 123", 30), ("판매일:26-1C-01 18:26,목요일", 50)],   # C 때문에 날짜 누락
    31: [("다라마트 노원점", 10), ("서울시 노원구 동일로 45", 30), ("2026-10-20 12:30", 50)],         # 날짜 정상
    32: [("마바카페", 10), ("TEL 02-1234-5678", 30), ("12:00", 50)],                                  # 날짜 없음
}


class FakeEngine:
    def __init__(self, cfg, allow_download=False, expect_version=None):
        self.init_s, self.version, self.version_warning, self.kwargs, self.optional_models = 0.0, "fake", None, {}, {}

    def run(self, img):
        toks = [engine.OcrToken(t, 0.95, [[0, y], [100, y], [100, y + 10], [0, y + 10]]) for t, y in TOKS[img.shape[1]]]
        return engine.OcrOutput(toks, 1.0, None)


def _make_run(d):
    imgs = os.path.join(d, "dev")
    os.makedirs(imgs)
    for i, w in enumerate(TOKS):
        cv2.imwrite(os.path.join(imgs, f"r{i}.jpg"), np.full((40, w, 3), 255, np.uint8))
    lab = os.path.join(d, "labels.csv")
    open(lab, "w", encoding="utf-8").write(
        "file,date,time,merchant_name,address_road,address_complete\n"
        "r0.jpg,2026-10-01,18:26,가나식당,서울시 노원구 동일로,true\n"
        "r1.jpg,2026-10-20,12:30,다라마트 노원점,서울시 노원구 동일로,true\n"
        "r2.jpg,,12:00,마바카페,-,false\n")
    out = os.path.join(d, "res")
    a = argparse.Namespace(images=[imgs], labels=[lab], data_kind="real", out=out, warmup=0, repeat=1, dry_run=False,
                           config=None, mode="baseline", set=[], device=None, allow_download=False, save_values=False,
                           diag_dir=None, save_raw=True, final=False)
    orig = engine.OcrEngine
    engine.OcrEngine = FakeEngine
    try:
        evaluate.run_eval(a)
    finally:
        engine.OcrEngine = orig
    return lab, out


def _rescore(lab, runs, out=None, sets=("fields.date_lookalike_fix=fallback",)):
    cmd = [sys.executable, os.path.join(ROOT, "rescore_raw.py"), "--labels", lab, "--runs", *runs]
    for s in sets:
        cmd += ["--set", s]
    if out:
        cmd += ["--out", out]
    return subprocess.run(cmd, capture_output=True, text=True, cwd=os.environ.get("LAB_PROJECT", os.getcwd()))


def test_rescore_reproduces_then_compares():
    d = tempfile.mkdtemp()
    lab, run = _make_run(d)
    m = json.load(open(os.path.join(run, "metrics.json"), encoding="utf-8"))["fields"]
    assert m["date"]["exact"] == 1 and m["date"]["miss"] == 1          # 기존 규칙: r0 날짜 누락
    r = _rescore(lab, [run], out=os.path.join(d, "cmp"))
    assert r.returncode == 0, r.stdout + r.stderr
    assert "재현 확인" in r.stdout and "날짜: 1/2 → 2/2" in r.stdout
    rep = json.load(open(os.path.join(d, "cmp", "rescore.json"), encoding="utf-8"))["runs"][run]
    assert rep["fixed_images"] == ["r0.jpg"]
    assert {(c["file"], c["field"], c["before"], c["after"]) for c in rep["judgement_changes"]} == \
           {("r0.jpg", "date", "miss", "exact"), ("r0.jpg", "status", "PARTIAL", "SUCCESS")}
    assert "판매일" not in open(os.path.join(d, "cmp", "rescore.json"), encoding="utf-8").read()   # 원문 없음
    assert "판매일" not in r.stdout


def test_rescore_stops_when_not_reproduced_and_guards():
    d = tempfile.mkdtemp()
    lab, run = _make_run(d)
    p = os.path.join(run, "rows.csv")                                   # 저장된 판정을 일부러 바꿈
    open(p, "w", encoding="utf-8-sig").write(open(p, encoding="utf-8-sig").read().replace("miss", "exact", 1))
    r = _rescore(lab, [run])
    assert r.returncode == 2 and "[멈춤]" in r.stdout
    assert "holdout" in _rescore(lab, [os.path.join(d, "holdout2_x")]).stderr
    os.makedirs(os.path.join(d, "exists"))
    assert "덮어쓰지 않음" in _rescore(lab, [run], out=os.path.join(d, "exists")).stderr
