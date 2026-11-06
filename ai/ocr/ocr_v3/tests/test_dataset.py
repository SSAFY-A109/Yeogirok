"""데이터 목록 검사·영수증 단위 분할·det/rec 내보내기 (합성 이미지, GPU 불필요)."""
import json
import os
import tempfile

import cv2
import numpy as np

from ocrlab import dataset

BOX = [[10, 10], [110, 10], [110, 40], [10, 40]]


def _img(path, seed, shift=0):
    rng = np.random.default_rng(seed)
    a = np.full((120, 200, 3), 255, np.uint8)
    for _ in range(6):
        x, y = rng.integers(0, 150), rng.integers(0, 90)
        cv2.rectangle(a, (int(x), int(y)), (int(x) + 40, int(y) + 20), (0, 0, 0), -1)
    a = np.clip(a.astype(int) + shift, 0, 255).astype(np.uint8)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    cv2.imwrite(path, a)


def _rec(img, rid, src="human_verified", lines=None):
    return {"image": img, "receipt_id": rid, "label_source": src, "split": None, "_line": 1,
            "lines": lines if lines is not None else [{"text": "가나식당", "points": BOX}]}


def _setup():
    d = tempfile.mkdtemp()
    for i in range(10):
        _img(os.path.join(d, f"r{i}.png"), i)
    _img(os.path.join(d, "r0_again.png"), 0, shift=4)     # r0 과 거의 같은 사진 (다른 receipt_id 로 잘못 적음)
    open(os.path.join(d, "broken.jpg"), "wb").write(b"\xff\xd8 broken")
    return d


def test_check_finds_problems():
    d = _setup()
    recs = [_rec("r0.png", "A"), _rec("missing.png", "B"), _rec("broken.jpg", "C"),
            _rec("r1.png", "D", lines=[{"text": "가", "points": [[0, 0], [5, 5]]}]),
            _rec("r2.png", "E", lines=[{"text": "가", "points": [[0, 0], [500, 0], [500, 20], [0, 20]]}]),
            _rec("r3.png", "F", lines=[{"text": "", "points": BOX}]),
            _rec("r4.png", "", lines=[{"text": "가", "points": [[0, 0], [float("nan"), 0], [5, 5], [0, 5]]}]),
            _rec("r5.png", "G", src="guess"), _rec("r6.png", "H", lines=[]),
            _rec("r7.png", "I", lines=[{"text": "가나★", "points": BOX}]),
            _rec("r0_again.png", "Z")]
    rep = dataset.check(recs, d, dict_chars=["가", "나", "식", "당"], max_len=3)
    e = "\n".join(rep["errors"])
    for frag in ("이미지 파일 없음", "손상", "4개 미만", "이미지 밖", "비어 있음", "NaN", "receipt_id 없음",
                 "label_source", "라벨 줄이 없음"):
        assert frag in e, frag
    w = "\n".join(rep["warnings"])
    assert "사전에 없는 글자" in w and "★" in w and "최대 3자" in w and "비슷한 사진" in w
    assert rep["stats"]["images"] == 11


def test_split_keeps_receipts_and_near_duplicates_together():
    d = _setup()
    recs = [_rec(f"r{i}.png", f"R{i // 2}") for i in range(10)] + [_rec("r0_again.png", "Z"),
                                                                    _rec("r9.png", "C9", src="auto_candidate")]
    out = dataset.split(recs, d, (0.6, 0.2, 0.2), seed=1)
    by = {r["image"] + r["receipt_id"]: r["split"] for r in out}
    for k in range(5):                                         # 같은 receipt_id 는 같은 분할
        assert by[f"r{2 * k}.pngR{k}"] == by[f"r{2 * k + 1}.pngR{k}"]
    assert by["r0_again.pngZ"] == by["r0.pngR0"]               # 비슷한 사진은 receipt_id 가 달라도 같은 분할
    assert by["r9.pngC9"] == "review"                          # 검수 전 후보는 학습·평가에서 제외
    assert {"train", "val", "test"} >= {v for v in by.values() if v != "review"}
    again = dataset.split([dict(r) for r in recs], d, (0.6, 0.2, 0.2), seed=1)
    assert [r["split"] for r in again] == [r["split"] for r in out]      # 같은 seed → 같은 결과


def test_export_det_and_rec():
    d = _setup()
    recs = [_rec(f"r{i}.png", f"R{i}", lines=[{"text": "가나식당", "points": BOX},
                                              {"text": "", "points": BOX, "ignore": True},
                                              {"text": "가나★", "points": BOX}]) for i in range(4)]
    for r, s in zip(recs, ("train", "train", "val", "test")):
        r["split"] = s
    od = os.path.join(d, "det")
    meta = dataset.export_det(recs, d, od)
    line = open(os.path.join(od, "train.txt"), encoding="utf-8").readline()
    path, ann = line.rstrip("\n").split("\t")
    ann = json.loads(ann)
    assert path == "r0.png" and ann[0]["transcription"] == "가나식당" and ann[1]["transcription"] == "###"
    assert len(ann[0]["points"]) == 4 and meta["counts"] == {"train": 2, "val": 1, "test": 1}
    orc = os.path.join(d, "rec")
    m2 = dataset.export_rec(recs, d, orc, dict_chars=["가", "나", "식", "당"])
    rows = open(os.path.join(orc, "train.txt"), encoding="utf-8").read().splitlines()
    assert len(rows) == 2 and rows[0].endswith("\t가나식당") and m2["skipped"]["사전 밖 글자"] == 4
    crop = cv2.imread(os.path.join(orc, rows[0].split("\t")[0]))
    assert crop.shape[:2] == (30, 100)
    assert json.load(open(os.path.join(orc, "export_meta.json")))["sources"] == ["human_verified"]


def test_candidates_cannot_be_exported_or_trained():
    d = _setup()
    r = _rec("r0.png", "A", src="auto_candidate")
    r["split"] = "train"
    for fn in (dataset.export_det, dataset.export_rec):
        try:
            fn([r], d, os.path.join(d, "x"), sources=("human_verified", "auto_candidate"))
            raise AssertionError("후보 내보내기 통과")
        except ValueError:
            pass
    assert dataset.export_det([r], d, os.path.join(d, "y"))["counts"]["train"] == 0


def test_ppocrlabel_import_and_promote():
    d = tempfile.mkdtemp()
    p = os.path.join(d, "Label.txt")
    open(p, "w", encoding="utf-8").write(
        "imgs/a.jpg\t" + json.dumps([{"transcription": "가나", "points": BOX, "difficult": False},
                                     {"transcription": "###", "points": BOX, "difficult": False}], ensure_ascii=False) + "\n")
    recs = dataset.import_ppocrlabel(p, "human_verified")
    assert recs[0]["receipt_id"] == "a" and recs[0]["lines"][1]["ignore"] is True
    cands = [{"label_source": "auto_candidate", "reviewed": True}, {"label_source": "auto_candidate", "reviewed": False}]
    out, n = dataset.promote(cands, "김재원")
    assert n == 1 and out[0]["label_source"] == "human_verified" and out[1]["label_source"] == "auto_candidate"
    try:
        dataset.promote(cands, "")
        raise AssertionError("검수자 없이 통과")
    except ValueError:
        pass


def test_manifest_roundtrip_and_bad_json():
    d = tempfile.mkdtemp()
    p = os.path.join(d, "m.jsonl")
    dataset.write_manifest([_rec("a.png", "A")], p)
    assert dataset.read_manifest(p)[0]["receipt_id"] == "A" and "_line" not in open(p).read()
    open(p, "a").write("{bad json\n")
    try:
        dataset.read_manifest(p)
        raise AssertionError
    except ValueError as e:
        assert ":2" in str(e)


def test_export_with_nothing_trainable_makes_empty_meta():
    d = _setup()
    r = _rec("r0.png", "A", src="auto_candidate")
    r["split"] = "review"
    out = os.path.join(d, "rec_none")
    m = dataset.export_rec([r], d, out)
    assert m["counts"] == {"train": 0, "val": 0, "test": 0} and os.path.exists(os.path.join(out, "export_meta.json"))
