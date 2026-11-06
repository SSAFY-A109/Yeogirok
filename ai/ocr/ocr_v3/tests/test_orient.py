"""사진 방향 보정 (pre.orient_retry). 가짜 OCR 엔진으로 확인, GPU 없음, 가상 영수증 값.
사진 왼쪽 위 검은 점의 위치로 '어느 방향으로 찍혔는지'를 흉내 낸다 (왼쪽 위 = 바른 방향)."""
import argparse
import json
import os
import tempfile

import cv2
import numpy as np

from ocrlab import engine, evaluate, orient
from ocrlab.settings import load_config

GOOD = [("가나식당", 10), ("서울시 노원구 동일로 123", 30), ("거래일시 2026-10-20 12:30", 50), ("합계 9,000", 70), ("카드 승인", 90)]
BAD = [("ㅁ녀ㅕ ld", 10), ("##ㄷ 0z", 30), ("코EI', 7", 50)]


def marked(h=60, w=40):
    img = np.full((h, w, 3), 255, np.uint8)
    img[:6, :6] = 0                                       # 바른 방향 표시: 왼쪽 위
    return img


def corner(img):
    h, w = img.shape[:2]
    blk = {"TL": img[:6, :6], "TR": img[:6, w - 6:], "BL": img[h - 6:, :6], "BR": img[h - 6:, w - 6:]}
    return min(blk, key=lambda k: blk[k].mean())


def toks(rows, tall=False):
    out = []
    for t, y in rows:
        poly = [[0, y], [8, y], [8, y + 40], [0, y + 40]] if tall else [[0, y], [100, y], [100, y + 10], [0, y + 10]]
        out.append(engine.OcrToken(t, 0.95, poly))
    return out


class Fake:
    """바른 방향이면 GOOD, 옆으로 누우면(sideways=True 일 때 TR) 글자는 읽히지만 세로 상자, 그 밖은 깨진 글자."""
    def __init__(self, sideways_ok=False):
        self.calls, self.sideways_ok = 0, sideways_ok
        self.init_s, self.version, self.version_warning, self.kwargs, self.optional_models = 0.0, "fake", None, {}, {}

    def run(self, img):
        self.calls += 1
        c = corner(img)
        if c == "TL":
            return engine.OcrOutput(toks(GOOD), 1.0, None)
        if self.sideways_ok and c == "BL":
            return engine.OcrOutput(toks(GOOD, tall=True), 1.0, None)
        return engine.OcrOutput(toks(BAD), 1.0, None)


def test_default_is_legacy():
    assert load_config()["pre"]["orient_retry"] == "legacy" and load_config()["_changed"] == {}


def test_upright_photo_not_retried():
    e = Fake()
    img = marked()
    out, meta = orient.run(e, img, e.run(img))
    assert meta["triggered"] is False and meta["angle"] == 0 and e.calls == 1


def test_upside_down_photo_rotated_180():
    e = Fake()
    img = cv2.rotate(marked(), cv2.ROTATE_180)
    out, meta = orient.run(e, img, e.run(img))
    assert meta["triggered"] and meta["angle"] == 180
    assert [t.text for t in out.tokens] == [t for t, _ in GOOD] and e.calls == 4


def test_sideways_photo_with_tall_boxes_rotated():
    e = Fake(sideways_ok=True)
    img = cv2.rotate(marked(), cv2.ROTATE_90_COUNTERCLOCKWISE)      # 점이 왼쪽 아래로 감
    first = e.run(img)
    assert orient.vertical_ratio(first.tokens) >= orient.VERTICAL_RATIO
    out, meta = orient.run(e, img, first)
    assert meta["angle"] == 90 and orient.vertical_ratio(out.tokens) == 0.0


def test_all_garbage_keeps_original():
    class AllBad(Fake):
        def run(self, img):
            self.calls += 1
            return engine.OcrOutput(toks(BAD), 1.0, None)
    e = AllBad()
    img = marked()
    first = e.run(img)
    out, meta = orient.run(e, img, first)
    assert meta["triggered"] and meta["angle"] == 0 and out is first


def test_eval_with_orient_retry_records_angle():
    d = tempfile.mkdtemp()
    imgs = os.path.join(d, "dev")
    os.makedirs(imgs)
    cv2.imwrite(os.path.join(imgs, "a.jpg"), marked())
    cv2.imwrite(os.path.join(imgs, "b.jpg"), cv2.rotate(marked(), cv2.ROTATE_180))
    lab = os.path.join(d, "labels.csv")
    open(lab, "w", encoding="utf-8").write("file,date,time,merchant_name\na.jpg,2026-10-20,12:30,가나식당\nb.jpg,2026-10-20,12:30,가나식당\n")
    orig = engine.OcrEngine
    engine.OcrEngine = lambda *a, **k: Fake()
    try:
        res = {}
        for mode in ("legacy", "auto"):
            out = os.path.join(d, mode)
            evaluate.run_eval(argparse.Namespace(
                images=[imgs], labels=[lab], data_kind="real", out=out, warmup=0, repeat=1, dry_run=False, config=None,
                mode="baseline", set=[f"pre.orient_retry={mode}"], device=None, allow_download=False, save_values=False,
                diag_dir=None, save_raw=False, final=False))
            res[mode] = json.load(open(os.path.join(out, "metrics.json"), encoding="utf-8"))
    finally:
        engine.OcrEngine = orig
    assert res["legacy"]["fields"]["name"]["exact"] == 1 and res["legacy"]["orient"]["rotated_n"] == 0
    assert res["auto"]["fields"]["name"]["exact"] == 2 and res["auto"]["fields"]["date"]["exact"] == 2
    assert res["auto"]["orient"] == {"mode": "auto", "retried_n": 1, "rotated_n": 1}
