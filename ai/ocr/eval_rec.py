#!/usr/bin/env python3
"""인식 모델(rec)만 따로 평가: 잘라낸 줄 이미지 → 글자 인식 → 정답과 비교 (CER / WER / 줄 정확도).

make_rec_data.py 가 만든 rec_data/test.txt 를 사용한다.
  CER : 글자 오류율 = 편집거리(글자) / 정답 글자 수   (낮을수록 좋음)
  WER : 단어 오류율 = 편집거리(띄어쓰기 단위) / 정답 단어 수
  line_acc : 줄 전체가 정확히 맞은 비율
  *_nospace : 띄어쓰기를 무시하고 계산 (영수증은 공백 개수가 들쭉날쭉해서 함께 본다)

예) python eval_rec.py --tag v0                                   # 사전학습 모델
    python eval_rec.py --tag v1 --model-dir output/rec_v1/inference  # 파인튜닝 모델
    python eval_rec.py --tag v0 --mlflow-run v0_pretrained            # 기존 MLflow run 에 지표 추가
"""
from __future__ import annotations

import argparse
import csv
import json
import os
import time
from collections import Counter, defaultdict
from difflib import SequenceMatcher


def edit_distance(a, b):
    if len(a) < len(b):
        a, b = b, a
    prev = list(range(len(b) + 1))
    for i, ca in enumerate(a, 1):
        cur = [i]
        for j, cb in enumerate(b, 1):
            cur.append(min(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + (ca != cb)))
        prev = cur
    return prev[-1]


def confusions(pred, gold, counter):
    for op, i1, i2, j1, j2 in SequenceMatcher(None, gold, pred, autojunk=False).get_opcodes():
        if op == "replace" and (i2 - i1) == (j2 - j1):
            for g, p in zip(gold[i1:i2], pred[j1:j2]):
                counter[(g, p)] += 1


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--data", default="rec_data")
    ap.add_argument("--split", default="test")
    ap.add_argument("--tag", required=True, help="결과 폴더 이름 (예: v0, v1)")
    ap.add_argument("--model-name", default="korean_PP-OCRv5_mobile_rec")
    ap.add_argument("--model-dir", default=None, help="파인튜닝 후 내보낸 inference 모델 폴더")
    ap.add_argument("--device", default="gpu:0")
    ap.add_argument("--batch", type=int, default=128)
    ap.add_argument("--synthetic", default="synthetic", help="난이도별 집계용 라벨 폴더 (synthetic/labels)")
    ap.add_argument("--mlflow-run", default=None, help="이 이름의 MLflow run 에 지표를 추가 기록")
    ap.add_argument("--tracking-uri", default="sqlite:///mlflow.db")
    args = ap.parse_args()

    from paddleocr import TextRecognition

    items = [l.split("\t", 1) for l in open(os.path.join(args.data, f"{args.split}.txt"), encoding="utf-8").read().splitlines() if "\t" in l]
    kw = {"model_name": args.model_name, "device": args.device}
    if args.model_dir:
        kw["model_dir"] = args.model_dir
    model = TextRecognition(**kw)

    preds, t0 = [], time.time()
    for s in range(0, len(items), 1024):
        chunk = [os.path.join(args.data, p) for p, _ in items[s:s + 1024]]
        for r in model.predict(input=chunk, batch_size=args.batch):
            get = r.get if hasattr(r, "get") else (lambda k, r=r: r[k])
            preds.append(get("rec_text"))
        print(f"{min(s + 1024, len(items))}/{len(items)} 줄 인식 ({time.time() - t0:.0f}s)")
    elapsed = time.time() - t0

    diff_of = {}
    for p, _ in items:
        rid = "_".join(os.path.basename(p).split("_")[1:3])          # s20261027_receipt_0001_003.png → receipt_0001
        if rid not in diff_of:
            lp = os.path.join(args.synthetic, "labels", rid + ".json")
            diff_of[rid] = json.load(open(lp, encoding="utf-8")).get("difficulty", "unknown") if os.path.exists(lp) else "unknown"

    tot = defaultdict(Counter)     # group → 누적 값
    conf = Counter()
    rows = []
    for (p, gold), pred in zip(items, preds):
        g_ns, p_ns = gold.replace(" ", ""), pred.replace(" ", "")
        ce, ce_ns = edit_distance(pred, gold), edit_distance(p_ns, g_ns)
        we = edit_distance(pred.split(), gold.split())
        rid = "_".join(os.path.basename(p).split("_")[1:3])
        for grp in ("all", f"difficulty_{diff_of.get(rid, 'unknown')}"):
            c = tot[grp]
            c["char_err"] += ce; c["chars"] += len(gold)
            c["char_err_ns"] += ce_ns; c["chars_ns"] += len(g_ns)
            c["word_err"] += we; c["words"] += max(1, len(gold.split()))
            c["lines"] += 1; c["line_ok"] += pred == gold; c["line_ok_ns"] += p_ns == g_ns
        if p_ns != g_ns:
            confusions(p_ns, g_ns, conf)
        rows.append({"image": p, "gold": gold, "pred": pred, "cer": round(ce / max(1, len(gold)), 4), "ok": pred == gold})

    def metrics(c):
        return {"cer": c["char_err"] / c["chars"], "cer_nospace": c["char_err_ns"] / c["chars_ns"],
                "wer": c["word_err"] / c["words"], "line_acc": c["line_ok"] / c["lines"],
                "line_acc_nospace": c["line_ok_ns"] / c["lines"]}

    out = os.path.join("results_rec", args.tag)
    os.makedirs(out, exist_ok=True)
    with open(os.path.join(out, "per_line.csv"), "w", encoding="utf-8-sig", newline="") as f:
        w = csv.DictWriter(f, fieldnames=list(rows[0].keys()))
        w.writeheader()
        w.writerows(rows)
    with open(os.path.join(out, "errors.csv"), "w", encoding="utf-8-sig", newline="") as f:
        w = csv.DictWriter(f, fieldnames=list(rows[0].keys()))
        w.writeheader()
        w.writerows(sorted((r for r in rows if not r["ok"]), key=lambda r: -r["cer"]))

    lines = [f"[{args.tag}] {args.split} {len(items)}줄   모델: {args.model_dir or args.model_name}   {elapsed:.0f}s "
             f"({elapsed / len(items) * 1000:.1f}ms/줄)", ""]
    lines.append(f"{'구분':<20}{'CER':>8}{'CER(공백무시)':>14}{'WER':>8}{'줄정확도':>10}{'줄(공백무시)':>13}")
    for grp in ["all"] + sorted(g for g in tot if g != "all"):
        m = metrics(tot[grp])
        lines.append(f"{grp:<20}{m['cer'] * 100:7.2f}%{m['cer_nospace'] * 100:13.2f}%{m['wer'] * 100:7.2f}%"
                     f"{m['line_acc'] * 100:9.2f}%{m['line_acc_nospace'] * 100:12.2f}%")
    lines += ["", "자주 틀리는 글자 (정답→인식, 횟수):",
              "  " + ", ".join(f"{g}→{p} {n}" for (g, p), n in conf.most_common(20))]
    report = "\n".join(lines)
    open(os.path.join(out, "summary.txt"), "w", encoding="utf-8").write(report)
    print("\n" + report)
    print(f"\n상세: {out}/per_line.csv, errors.csv")

    if args.mlflow_run:
        import mlflow
        mlflow.set_tracking_uri(args.tracking_uri)
        mlflow.set_experiment("receipt-ocr")
        found = mlflow.search_runs(experiment_names=["receipt-ocr"],
                                   filter_string=f"tags.mlflow.runName = '{args.mlflow_run}'")
        run_id = found.iloc[0]["run_id"] if len(found) else None
        with mlflow.start_run(run_id=run_id, run_name=None if run_id else args.mlflow_run):
            for grp, c in tot.items():
                pre = "rec" if grp == "all" else f"rec_{grp}"
                for k, v in metrics(c).items():
                    mlflow.log_metric(f"{pre}_{k}", v)
            mlflow.log_metric("rec_ms_per_line", elapsed / len(items) * 1000)
            mlflow.log_metric("rec_test_lines", len(items))
            for fn in ("summary.txt", "errors.csv"):
                mlflow.log_artifact(os.path.join(out, fn), artifact_path="rec_eval")
        print(f"MLflow run '{args.mlflow_run}' 에 rec 지표 기록 완료")


if __name__ == "__main__":
    main()
