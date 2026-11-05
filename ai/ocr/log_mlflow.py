#!/usr/bin/env python3
"""평가 결과를 MLflow 실험으로 기록한다 (OCR을 다시 돌리지 않음).

eval_baseline.py(합성) / eval_real.py(실제) 가 만든 per_receipt.csv 를 읽어서
  - params : 모델·추론 설정·버전 정보
  - metrics: 날짜·시간·상호명 정확도 (전체 / 난이도별 / 템플릿별), 장당 처리 시간
  - artifacts: summary.txt, per_receipt.csv, 시각화 이미지(grid.jpg 등)
를 남긴다.

예) python log_mlflow.py --run-name v0_pretrained
    python log_mlflow.py --run-name v1_finetune --synthetic results_ocr_v1 --real results_real_v1 \
        --vis vis/v1_masked --param rec_model=./models/v1 --note "합성 줄 이미지로 인식 모델 파인튜닝"
UI) mlflow ui --backend-store-uri sqlite:///mlflow.db --port 5000
"""
from __future__ import annotations

import argparse
import csv
import os
import platform
import re
from collections import Counter, defaultdict

import mlflow

DEFAULT_PARAMS = {
    "det_model": "PP-OCRv5_server_det",
    "rec_model": "korean_PP-OCRv5_mobile_rec (pretrained)",
    "use_doc_orientation_classify": False,
    "use_doc_unwarping": False,
    "use_textline_orientation": False,
    "text_det_thresh": 0.2,
    "text_det_box_thresh": 0.4,
    "postprocess": "receipt_parser.py (regex + deskew line grouping)",
    "preprocess": "none",
    "augmentation": "none",
}
FIELDS = (("date", "date"), ("time", "time"), ("name", "merchant"))


def acc(counter):
    n = sum(v for k, v in counter.items() if k != "gold_null")
    return (counter["exact"] / n) if n else None


def read_rows(path):
    with open(path, encoding="utf-8-sig") as f:
        return list(csv.DictReader(f))


def seconds_from_summary(path):
    """summary.txt 의 '소요: 92s' / '소요 25s' 에서 초를 읽는다."""
    if not os.path.exists(path):
        return None
    m = re.search(r"소요:?\s*(\d+)s", open(path, encoding="utf-8").read())
    return int(m.group(1)) if m else None


def log_set(prefix, folder, group_cols=()):
    """prefix 예: synthetic / real. 정확도 metric 을 기록하고 요약 dict 반환."""
    csv_path = os.path.join(folder, "per_receipt.csv")
    if not os.path.exists(csv_path):
        print(f"[건너뜀] {csv_path} 없음")
        return None
    rows = read_rows(csv_path)
    out = {}
    for col, label in FIELDS:
        a = acc(Counter(r[col] for r in rows))
        if a is not None:
            mlflow.log_metric(f"{prefix}_{label}_acc", a)
            out[label] = a
    all3 = sum(all(r[c] in ("exact", "gold_null") for c, _ in FIELDS) for r in rows) / len(rows)
    mlflow.log_metric(f"{prefix}_all_fields_acc", all3)
    mlflow.log_metric(f"{prefix}_n_images", len(rows))
    for g in group_cols:                                   # 난이도·템플릿별
        buckets = defaultdict(lambda: defaultdict(Counter))
        for r in rows:
            for col, _ in FIELDS:
                buckets[r.get(g)][col][r[col]] += 1
        for key, cs in buckets.items():
            for col, label in FIELDS:
                a = acc(cs[col])
                if a is not None:
                    mlflow.log_metric(f"{prefix}_{g}_{key}_{label}_acc", a)
    sec = seconds_from_summary(os.path.join(folder, "summary.txt"))
    if sec:
        mlflow.log_metric(f"{prefix}_sec_per_image", sec / len(rows))
    for fn in ("summary.txt", "per_receipt.csv"):
        p = os.path.join(folder, fn)
        if os.path.exists(p):
            mlflow.log_artifact(p, artifact_path=prefix)
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--run-name", required=True, help="예: v0_pretrained, v1_finetune")
    ap.add_argument("--experiment", default="receipt-ocr")
    ap.add_argument("--tracking-uri", default="sqlite:///mlflow.db")
    ap.add_argument("--synthetic", default="results_ocr", help="eval_baseline.py --mode ocr 결과 폴더")
    ap.add_argument("--real", default="results_real", help="eval_real.py 결과 폴더")
    ap.add_argument("--vis", default="vis/v0_masked", help="시각화 폴더 (마스킹된 것만 올릴 것)")
    ap.add_argument("--param", action="append", default=[], help="설정 덮어쓰기 key=value (여러 번 가능)")
    ap.add_argument("--note", default="", help="실험 설명")
    args = ap.parse_args()

    params = dict(DEFAULT_PARAMS)
    for kv in args.param:
        k, v = kv.split("=", 1)
        params[k] = v
    try:
        import paddle
        import paddleocr
        params["paddleocr_version"] = paddleocr.__version__
        params["paddle_version"] = paddle.__version__
    except Exception:
        pass
    params["python"] = platform.python_version()
    params["gpu"] = os.environ.get("CUDA_VISIBLE_DEVICES", "?")

    mlflow.set_tracking_uri(args.tracking_uri)
    mlflow.set_experiment(args.experiment)
    with mlflow.start_run(run_name=args.run_name) as run:
        mlflow.log_params(params)
        mlflow.set_tags({"version": args.run_name.split("_")[0], "note": args.note,
                         "data.synthetic": "200 synthetic receipts (T01-T10)", "data.real": "10 real receipts"})
        syn = log_set("synthetic", args.synthetic, group_cols=("difficulty", "template"))
        real = log_set("real", args.real)
        if os.path.isdir(args.vis):
            if "masked" not in args.vis:
                print("[경고] 마스킹되지 않은 시각화 폴더입니다. 개인정보가 MLflow에 올라갑니다.")
            mlflow.log_artifacts(args.vis, artifact_path="vis")
        for f in ("receipt_parser.py", "eval_baseline.py", "requirements.lock.txt"):
            if os.path.exists(f):
                mlflow.log_artifact(f, artifact_path="code")
        print(f"\nMLflow run 기록 완료: {args.run_name}  (run_id={run.info.run_id})")
        for name, d in (("합성", syn), ("실제", real)):
            if d:
                print(f"  {name}: " + "  ".join(f"{k} {v * 100:.1f}%" for k, v in d.items()))


if __name__ == "__main__":
    main()
