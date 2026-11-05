#!/usr/bin/env python3
"""실제 영수증 평가 (날짜·시간·상호명).

1) 정답표 양식 만들기:  python eval_real.py --images ./real --make-template
   → real_labels.csv 가 생기면 엑셀/주피터에서 date, time, merchant_name 칸을 채운다.
      - date: YYYY-MM-DD, time: HH:MM (영수증에 없으면 빈칸), merchant_name: 영수증에 인쇄된 상호명 그대로
2) 평가:                python eval_real.py --images ./real --labels real_labels.csv
"""
from __future__ import annotations

import argparse
import csv
import json
import os
import time
from collections import Counter

from eval_baseline import judge, run_ocr_factory
from receipt_parser import boxes_from_polys, group_lines, parse_receipt

IMG_EXT = (".jpg", ".jpeg", ".png", ".bmp", ".webp")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--images", default="./real")
    ap.add_argument("--labels", default="real_labels.csv")
    ap.add_argument("--make-template", action="store_true")
    ap.add_argument("--device", default="gpu:0")
    ap.add_argument("--textline-orient", action="store_true")
    ap.add_argument("--out", default="results_real")
    ap.add_argument("--rec-model-dir", default=None, help="파인튜닝한 인식 모델(inference) 폴더")
    ap.add_argument("--paddle-default", action="store_true", help="출발점 재현: PaddleOCR 기본 설정 그대로")
    ap.add_argument("--no-group", action="store_true", help="출발점 재현: 줄 묶기 없이 OCR 박스 순서 그대로")
    args = ap.parse_args()

    files = sorted(f for f in os.listdir(args.images) if f.lower().endswith(IMG_EXT))
    if args.make_template:
        if os.path.exists(args.labels):
            print(f"{args.labels} 가 이미 있어서 덮어쓰지 않았습니다.")
            return
        with open(args.labels, "w", encoding="utf-8-sig", newline="") as f:
            w = csv.writer(f)
            w.writerow(["file", "date", "time", "merchant_name", "memo"])
            for fn in files:
                w.writerow([fn, "", "", "", ""])
        print(f"{args.labels} 생성: {len(files)}개 이미지. date/time/merchant_name 칸을 채워 주세요.")
        return

    gold = {r["file"]: r for r in csv.DictReader(open(args.labels, encoding="utf-8-sig"))}
    os.makedirs(os.path.join(args.out, "raw"), exist_ok=True)
    run = run_ocr_factory(args.device, args.textline_orient, args.rec_model_dir, args.paddle_default)
    rows, stats, t0 = [], {k: Counter() for k in ("date", "time", "name")}, time.time()
    for fn in files:
        if fn not in gold:
            print(f"[건너뜀] 정답표에 없음: {fn}")
            continue
        g = gold[fn]
        texts, scores, polys = run(os.path.join(args.images, fn))
        json.dump({"texts": texts, "scores": [float(s) for s in scores], "polys": polys},
                  open(os.path.join(args.out, "raw", fn + ".json"), "w", encoding="utf-8"), ensure_ascii=False)
        p = parse_receipt(list(zip(texts, scores)) if args.no_group
                         else group_lines(boxes_from_polys(texts, scores, polys)))
        gd, gt, gn = (g.get("date") or None), ((g.get("time") or "")[:5] or None), (g.get("merchant_name") or None)
        r = {"file": fn,
             "gold_date": gd, "pred_date": p.date, "date": judge(p.date, gd, "date"),
             "gold_time": gt, "pred_time": p.time, "time": judge(p.time, gt, "time"),
             "gold_name": gn, "pred_name": p.placeName, "name": judge(p.placeName, gn, "merchant")}
        rows.append(r)
        for k in stats:
            stats[k][r[k]] += 1
        print(f"{fn}: 날짜 {r['date']:<7} 시간 {r['time']:<7} 상호 {r['name']:<7} | {p.date} {p.time} {p.placeName}")

    if not rows:
        print("평가할 이미지가 없습니다.")
        return
    with open(os.path.join(args.out, "per_receipt.csv"), "w", encoding="utf-8-sig", newline="") as f:
        w = csv.DictWriter(f, fieldnames=list(rows[0].keys()))
        w.writeheader()
        w.writerows(rows)
    lines = [f"실제 영수증 {len(rows)}장   소요 {time.time() - t0:.0f}s", ""]
    for k, label in (("date", "날짜"), ("time", "시간"), ("name", "상호명")):
        c = stats[k]
        n = sum(v for kk, v in c.items() if kk != "gold_null")
        acc = c["exact"] / n * 100 if n else 0
        detail = ", ".join(f"{t}={c[t]}" for t in ("exact", "partial", "wrong", "miss", "gold_null") if c[t])
        lines.append(f"{label:<5} {acc:6.1f}%  ({detail})")
    report = "\n".join(lines)
    open(os.path.join(args.out, "summary.txt"), "w", encoding="utf-8").write(report)
    print("\n" + report)
    print(f"\n상세 결과: {args.out}/per_receipt.csv")


if __name__ == "__main__":
    main()
