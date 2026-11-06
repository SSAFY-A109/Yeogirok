"""평가 지표·CER 조건·평가 CLI(dry-run) (GPU 불필요)."""
import json
import os
import subprocess
import sys
import tempfile

import cv2
import numpy as np

from ocrlab import cer, evaluate, resources

LAB = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "lab.py")


def test_judge_and_summary():
    j = evaluate.judge_field
    assert j("address", "서울시  노원구 동일로", "서울시 노원구 동일로") == "exact"
    assert j("address", None, "서울시 노원구 동일로") == "miss"
    assert j("address", "서울시 노원구", None) == "false_pos" and j("address", None, None) == "null_ok"
    assert j("address", "x", "SKIP") == "skip" and j("name", "가나식당 노원점", "가나식당") == "partial"
    rows = [{"date": "exact", "time": "exact", "name": "exact", "address": "exact", "status": "SUCCESS",
             "addressComplete_pred": True, "addressComplete_gold": True},
            {"date": "exact", "time": "miss", "name": "wrong", "address": "skip", "status": "PARTIAL",
             "addressComplete_pred": False, "addressComplete_gold": None},
            {"date": "wrong", "time": "null_ok", "name": "partial", "address": "miss", "status": "PARTIAL",
             "addressComplete_pred": False, "addressComplete_gold": True}]
    s = evaluate.summarize(rows)
    assert s["date"]["exact_rate"] == 66.7 and s["date"]["misread_rate"] == 33.3
    assert s["time"]["miss_rate"] == 50.0 and s["time"]["null_ok"] == 1
    assert s["name"]["misread_rate"] == 66.7 and s["address"]["skipped"] == 1 and s["address"]["miss_rate"] == 50.0
    assert s["all3_fields_ok_rate"] == 33.3 and s["all4_fields_ok_rate"] == 50.0 and s["all4_evaluated"] == 2
    assert s["addressComplete_acc"] == 50.0 and s["status"] == {"SUCCESS": 1, "PARTIAL": 2, "FAILED": 0}


def test_gold_values_address_convention():
    g = {"file": "a", "date": "2026-10-20", "time": "12:30:11", "merchant_name": "가나", "address_road": "-",
         "address_complete": "false"}
    v = evaluate.gold_values(g, addr_given=True)
    assert v["address"] is None and v["time"] == "12:30" and v["address_complete"] is False
    assert evaluate.gold_values({**g, "address_road": ""}, True)["address"] == "SKIP"
    assert evaluate.gold_values(g, addr_given=False)["address"] == "SKIP"


def test_percentile():
    assert resources.percentile([1, 2, 3, 4], 50) == 2.5 and resources.percentile(list(range(101)), 95) == 95


def test_cer_math_and_gate():
    assert cer.edit_distance("가나다", "가다") == 1
    r = cer.cer(["가나 다", "라마"], ["가나다", "라마"])
    assert r["cer"] == 20.0 and r["cer_nospace"] == 0.0 and r["line_acc"] == 50.0
    d = tempfile.mkdtemp()
    lab = os.path.join(d, "test.txt")
    assert cer.gate(lab)[0] is False                                     # 정답 없음
    open(lab, "w").write("crops/a.png\t가나\n")
    assert "출처" in cer.gate(lab)[1]                                    # 출처 기록 없음
    for srcs, allow, ok in ((["human_verified"], (), True), (["synthetic_generated"], (), False),
                            (["synthetic_generated"], ("synthetic_generated",), True),
                            (["human_verified", "auto_candidate"], (), False)):
        json.dump({"sources": srcs}, open(os.path.join(d, "export_meta.json"), "w"))
        assert cer.gate(lab, allow)[0] is ok, srcs
    assert cer.run(os.path.join(d, "none.txt"))["skipped"]


def test_eval_cli_dry_run_and_holdout_guard():
    d = tempfile.mkdtemp()
    imgs = os.path.join(d, "dev")
    os.makedirs(imgs)
    for i in range(3):
        cv2.imwrite(os.path.join(imgs, f"a{i}.jpg"), np.full((40, 30, 3), 255, np.uint8))
    lab = os.path.join(d, "labels.csv")
    open(lab, "w", encoding="utf-8").write("file,date,time,merchant_name,address_road\n"
                                           "a0.jpg,2026-10-20,12:30,가나,\na1.jpg,,,,\n")
    out = os.path.join(d, "res")
    cwd = os.environ.get("LAB_PROJECT", os.getcwd())
    r = subprocess.run([sys.executable, LAB, "eval", "--images", imgs, "--labels", lab, "--data-kind", "real",
                        "--out", out, "--dry-run", "--warmup", "1"], capture_output=True, text=True, cwd=cwd)
    assert r.returncode == 0, r.stderr
    m = json.load(open(os.path.join(out, "metrics.json"), encoding="utf-8"))
    assert m["n_images"] == 2 and m["fields"]["date"]["miss_rate"] == 100.0
    assert m["fields"]["address"]["evaluated"] == 0 and "주소 정답이 입력되지 않아" in r.stdout
    assert m["warmup_runs"] == 1 and m["samples"] == 2 and m["input_long_side"]["max"] == 40
    h = os.path.join(d, "holdout2")
    os.makedirs(h)
    r2 = subprocess.run([sys.executable, LAB, "eval", "--images", h, "--data-kind", "real", "--out", out + "2",
                         "--dry-run"], capture_output=True, text=True, cwd=cwd)
    assert r2.returncode != 0 and "--final" in (r2.stderr + r2.stdout)


def test_triage_categories():
    from ocrlab.triage import ACTION, classify
    ocr = ["가나식당", "서울시 노원구 동일로 123", "2026-10-20 12:30"]
    assert classify("wrong", "서울시 노원구 동일로", ocr)[0] == "선택·파싱"
    assert classify("miss", "서울시 노원구 동밀로", ocr)[0] == "인식 오류"
    assert classify("miss", "부산시 해운대구 바다로", ocr)[0] == "검출 누락 또는 심한 오인식"
    assert classify("false_pos", None, ocr)[0] == "오검출" and classify("exact", "x", ocr)[0] == "정답"
    assert ACTION["검출 누락 또는 심한 오인식"].startswith("탐지") and ACTION["인식 오류"].startswith("인식")


def test_tall_boxes_and_presence():
    from ocrlab.engine import OcrToken
    box = lambda h: OcrToken("가", 0.9, [[0, 0], [10, 0], [10, h], [0, h]])
    assert evaluate.tall_boxes([box(90)] * 10 + [box(381), box(1258)]) == 2
    assert evaluate.tall_boxes([box(90)] * 3) == 0
    rows = [{"repeat": 0, "has_placeName": True, "has_date": True, "has_time": False, "has_address": True,
             "address_complete": True, "status": "PARTIAL", "n_tall_boxes": 2},
            {"repeat": 0, "has_placeName": False, "has_date": False, "has_time": False, "has_address": False,
             "address_complete": False, "status": "FAILED", "n_tall_boxes": 0}]
    p = evaluate.presence(rows)
    assert p["placeName"] == 1 and p["address_complete"] == 1 and p["status"]["FAILED"] == 1
    assert p["images_with_tall_boxes"] == 1 and p["n"] == 2


def test_address_type_and_corp_mark():
    g = {"file": "a", "date": "", "time": "", "merchant_name": "㈜가나유통 노원점", "address_road": "서울 노원구 동일로",
         "address_type": "건물번호"}
    v = evaluate.gold_values(g, True)
    assert v["address_complete"] is True
    assert evaluate.gold_values({**g, "address_type": "지번"}, True)["address_complete"] is False
    assert evaluate.gold_values({**g, "address_type": ""}, True)["address_complete"] is None
    assert evaluate.judge_field("name", "(주)가나유통 노원점", v["name"]) == "exact"


def test_unreadable_fields_are_skipped():
    g = {"file": "a", "date": "2026-10-20", "time": "판독불가", "merchant_name": "판독불가",
         "address_road": "판독불가", "address_type": "판독불가"}
    v = evaluate.gold_values(g, True)
    assert v["time"] == "SKIP" and v["name"] == "SKIP" and v["address"] == "SKIP" and v["address_complete"] is None
    assert evaluate.judge_field("time", "15:47", v["time"]) == "skip"
    rows = [{"date": "exact", "time": "skip", "name": "exact", "address": "exact", "status": "SUCCESS",
             "addressComplete_pred": True, "addressComplete_gold": None},
            {"date": "exact", "time": "exact", "name": "exact", "address": "exact", "status": "SUCCESS",
             "addressComplete_pred": True, "addressComplete_gold": True}]
    s = evaluate.summarize(rows)
    assert s["time"]["evaluated"] == 1 and s["all3_evaluated"] == 1 and s["all3_fields_ok_rate"] == 100.0
