#!/usr/bin/env python3
"""영수증 OCR 기본 성능 평가 (날짜·시간·상호명).

두 가지 모드:
  --mode ocr     : PaddleOCR로 이미지를 읽고 → receipt_parser로 추출 → 정답과 비교 (실제 파이프라인 성능)
  --mode oracle  : 정답 JSON의 full_text(완벽한 OCR이라고 가정)를 파서에 넣어 비교 (파서 자체 성능)
두 결과를 비교하면 틀린 원인이 'OCR 글자 인식'인지 '후처리 규칙'인지 구분할 수 있다.

예)
  python eval_baseline.py --data ./synthetic --mode oracle
  python eval_baseline.py --data ./synthetic --mode ocr --limit 20
"""
from __future__ import annotations

import argparse
import csv
import json
import os
import time
from collections import Counter, defaultdict

from receipt_parser import boxes_from_polys, group_lines, parse_receipt


def norm_name(s):
    return "".join((s or "").split()).lower()


def gold_fields(lb):
    dt = lb.get("transaction_datetime")
    date = dt[:10] if dt else None
    tm = dt[11:16] if dt and len(dt) >= 16 else None   # 시:분까지만 비교 (파서는 초를 반환하지 않음)
    return date, tm, lb.get("merchant_name")


def judge(pred, gold, kind):
    if gold is None:
        return "gold_null"
    if pred is None:
        return "miss"
    if kind == "merchant":
        p, g = norm_name(pred), norm_name(gold)
        if p == g:
            return "exact"
        if p and (p in g or g in p):
            return "partial"
        return "wrong"
    return "exact" if pred == gold else "wrong"


def run_ocr_factory(device, textline_orient=False, rec_model_dir=None, paddle_default=False):
    from paddleocr import PaddleOCR
    if paddle_default:   # 출발점 재현용: 아무 설정도 바꾸지 않은 PaddleOCR 기본값 그대로
        ocr = PaddleOCR(lang="korean", device=device)

        def run_default(path):
            res = ocr.predict(path)[0]
            get = res.get if hasattr(res, "get") else (lambda k: res[k])
            return list(get("rec_texts")), list(get("rec_scores")), [p.tolist() if hasattr(p, "tolist") else p
                                                                     for p in get("rec_polys")]
        return run_default
    # 영수증은 대부분 똑바로 찍혀서, 텍스트 줄 방향 분류를 켜면 일부 줄을 180도 뒤집어 읽는 오류가 생김 → 기본 off
    kw = dict(device=device, use_doc_orientation_classify=False, use_doc_unwarping=False,
              use_textline_orientation=textline_orient, text_det_thresh=0.2, text_det_box_thresh=0.4)
    if rec_model_dir:   # 파인튜닝한 인식 모델 사용 (검출 모델은 그대로)
        kw.update(text_detection_model_name="PP-OCRv5_server_det",
                  text_recognition_model_name="korean_PP-OCRv5_mobile_rec",
                  text_recognition_model_dir=rec_model_dir)
    else:
        kw["lang"] = "korean"
    ocr = PaddleOCR(**kw)

    def run(path):
        res = ocr.predict(path)[0]
        get = res.get if hasattr(res, "get") else (lambda k: res[k])
        return list(get("rec_texts")), list(get("rec_scores")), [p.tolist() if hasattr(p, "tolist") else p
                                                                 for p in get("rec_polys")]
    return run


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--data", default="./synthetic", help="images/ 와 labels/ 가 있는 폴더")
    ap.add_argument("--mode", choices=["ocr", "oracle"], default="ocr")
    ap.add_argument("--device", default="gpu:0")
    ap.add_argument("--limit", type=int, default=0, help="앞에서 N장만 (0=전체)")
    ap.add_argument("--textline-orient", action="store_true", help="텍스트 줄 방향 분류 켜기 (기본 꺼짐)")
    ap.add_argument("--out", default=None, help="결과 폴더 (기본: results_<mode>)")
    ap.add_argument("--rec-model-dir", default=None, help="파인튜닝한 인식 모델(inference) 폴더")
    ap.add_argument("--paddle-default", action="store_true", help="출발점 재현: PaddleOCR 기본 설정 그대로 (방향 분류 on, 기본 임계값)")
    ap.add_argument("--no-group", action="store_true", help="출발점 재현: 기울기 보정·줄 묶기 없이 OCR 박스 순서 그대로")
    args = ap.parse_args()

    out = args.out or f"results_{args.mode}"
    os.makedirs(os.path.join(out, "raw"), exist_ok=True)
    label_files = sorted(f for f in os.listdir(os.path.join(args.data, "labels")) if f.endswith(".json"))
    if args.limit:
        label_files = label_files[:args.limit]

    run = run_ocr_factory(args.device, args.textline_orient, args.rec_model_dir, args.paddle_default) if args.mode == "ocr" else None
    rows, stats = [], defaultdict(Counter)
    t0 = time.time()
    for i, lf in enumerate(label_files, 1):
        lb = json.load(open(os.path.join(args.data, "labels", lf), encoding="utf-8"))
        if args.mode == "oracle":
            lines = [(t, 1.0) for t in lb["full_text"].split("\n")]
        else:
            img = os.path.join(args.data, "images", lf.replace(".json", ".png"))
            texts, scores, polys = run(img)
            json.dump({"texts": texts, "scores": [float(s) for s in scores], "polys": polys},
                      open(os.path.join(out, "raw", lf), "w", encoding="utf-8"), ensure_ascii=False)
            lines = (list(zip(texts, scores)) if args.no_group
                     else group_lines(boxes_from_polys(texts, scores, polys)))
        pred = parse_receipt(lines)
        g_date, g_time, g_name = gold_fields(lb)
        r = {
            "id": lb["id"], "template": lb.get("source_template_id"), "difficulty": lb.get("difficulty"),
            "gold_date": g_date, "pred_date": pred.date, "date": judge(pred.date, g_date, "date"),
            "gold_time": g_time, "pred_time": pred.time, "time": judge(pred.time, g_time, "time"),
            "gold_name": g_name, "pred_name": pred.placeName, "name": judge(pred.placeName, g_name, "merchant"),
        }
        rows.append(r)
        for k in ("date", "time", "name"):
            stats[(k, "all")][r[k]] += 1
            stats[(k, r["difficulty"])][r[k]] += 1
            stats[(k, r["template"])][r[k]] += 1
        if i % 20 == 0:
            print(f"{i}/{len(label_files)}  ({time.time() - t0:.0f}s)")

    with open(os.path.join(out, "per_receipt.csv"), "w", encoding="utf-8-sig", newline="") as f:
        w = csv.DictWriter(f, fieldnames=list(rows[0].keys()))
        w.writeheader()
        w.writerows(rows)

    def acc(c):
        n = sum(v for k, v in c.items() if k != "gold_null")
        return (c["exact"] / n * 100) if n else 0.0, n

    lines = [f"모드: {args.mode}   이미지: {len(rows)}장   소요: {time.time() - t0:.0f}s", ""]
    groups = ["all", "easy", "medium", "hard"] + sorted({r["template"] for r in rows})
    lines.append(f"{'구분':<8}{'날짜':>10}{'시간':>10}{'상호명':>10}   (정확히 일치 %)")
    for g in groups:
        vals = []
        for k in ("date", "time", "name"):
            a, n = acc(stats[(k, g)])
            vals.append(f"{a:9.1f}%" if n else "        -")
        lines.append(f"{g:<8}" + "".join(vals))
    lines.append("")
    for k, label in (("date", "날짜"), ("time", "시간"), ("name", "상호명")):
        c = stats[(k, "all")]
        lines.append(f"{label}: " + ", ".join(f"{t}={c[t]}" for t in ("exact", "partial", "wrong", "miss")
                                              if c[t]))
    report = "\n".join(lines)
    open(os.path.join(out, "summary.txt"), "w", encoding="utf-8").write(report)
    print(report)
    print(f"\n상세 결과: {out}/per_receipt.csv")


if __name__ == "__main__":
    main()
