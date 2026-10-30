"""YOLO 학습 결과(ultralytics 폴더)를 receipt-ocr MLflow 실험에 기록한다. OCR 환경(venv)에서 실행.

python log_yolo_mlflow.py --run-dir runs/segment/runs_det/yolo_v1 --name yolo_v1_det
"""
import argparse
import csv
import glob
import json
import os
import re

import mlflow
import yaml


def clean(k):
    k = k.strip().replace("(B)", "_box").replace("(M)", "_mask")
    return re.sub(r"[^0-9A-Za-z_\-./ ]", "_", k)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--run-dir", default="runs/segment/runs_det/yolo_v1")
    ap.add_argument("--name", default="yolo_v1_det")
    ap.add_argument("--data-config", default="det_data/det_data_config.json")
    ap.add_argument("--tracking-uri", default="sqlite:///mlflow.db")
    ap.add_argument("--experiment", default="receipt-ocr")
    ap.add_argument("--no-weights", action="store_true")
    a = ap.parse_args()

    mlflow.set_tracking_uri(a.tracking_uri)
    mlflow.set_experiment(a.experiment)
    rows = list(csv.DictReader(open(os.path.join(a.run_dir, "results.csv"))))
    rows = [{k.strip(): v for k, v in r.items()} for r in rows]
    targs = yaml.safe_load(open(os.path.join(a.run_dir, "args.yaml")))

    with mlflow.start_run(run_name=a.name):
        mlflow.set_tags({"stage": "detect", "task": "receipt-seg", "jira": "S15P31A109-73",
                         "framework": "ultralytics", "license_note": "AGPL - service use pending coach check"})
        keep = ["model", "data", "epochs", "imgsz", "batch", "seed", "optimizer", "lr0", "patience",
                "degrees", "fliplr", "mosaic", "close_mosaic"]
        mlflow.log_params({f"yolo_{k}": targs.get(k) for k in keep if k in targs})
        if os.path.exists(a.data_config):
            cfg = json.load(open(a.data_config, encoding="utf-8"))
            mlflow.log_params({f"det_data_{k}": (json.dumps(v, ensure_ascii=False)[:250] if isinstance(v, (dict, list)) else v)
                               for k, v in cfg.items()})
            mlflow.log_artifact(a.data_config, "det_data")

        for r in rows:
            step = int(float(r["epoch"]))
            mlflow.log_metrics({clean(k): float(v) for k, v in r.items()
                                if k not in ("epoch",) and v not in ("", None)}, step=step)

        key = "metrics/mAP50-95(M)"
        best = max(rows, key=lambda r: float(r[key]))
        last = rows[-1]
        mlflow.log_metrics({
            "best_mask_mAP50_95": float(best[key]),
            "best_epoch": float(best["epoch"]),
            "final_mask_mAP50_95": float(last[key]),
            "final_box_mAP50_95": float(last["metrics/mAP50-95(B)"]),
        })

        for f in ["results.csv", "args.yaml"] + sorted(glob.glob(os.path.join(a.run_dir, "*.png"))) \
                + sorted(glob.glob(os.path.join(a.run_dir, "*.jpg"))):
            p = f if os.path.isabs(f) or os.path.exists(f) else os.path.join(a.run_dir, f)
            if os.path.exists(p):
                mlflow.log_artifact(p, "train")
        if not a.no_weights:
            mlflow.log_artifact(os.path.join(a.run_dir, "weights", "best.pt"), "weights")
        print("기록 완료:", a.name, "| best mask mAP50-95", best[key], "@ epoch", best["epoch"])


if __name__ == "__main__":
    main()
