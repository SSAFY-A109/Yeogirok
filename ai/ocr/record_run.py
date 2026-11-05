#!/usr/bin/env python3
"""실험 기록 한 번에: MLflow 보강 + 포트폴리오 비교 자료 + 다운로드용 묶음.

평가(eval_baseline / eval_real / eval_rec / log_mlflow)를 끝낸 뒤 실행한다.
  1) MLflow: 각 run 에 학습 곡선(train_acc, train_loss, val_acc), val_best_acc,
     학습 로그·설정 파일·증강 설정을 추가 기록
  2) 비교표: 버전별 합성 200장 / 실제 10장 필드 정확도 + 인식 CER·WER·줄 정확도
     → compare.md, compare.csv (MLflow 마지막 run 에도 첨부)
  3) 학습 곡선 그래프 png (버전별 val acc, train loss)
  4) 실제 영수증 시각화 버전별 나란히 (마스킹본만 사용)
  5) 다운로드용 묶음 tar.gz (OCR 원문 raw 폴더는 넣지 않음)

예) python record_run.py --tags v0 v1 v2
출력: portfolio_record/<날짜_시각>/ , ~/portfolio_record_<날짜_시각>.tar.gz
"""
from __future__ import annotations

import argparse
import csv
import glob
import json
import os
import re
import shutil
import tarfile
import time

ap = argparse.ArgumentParser()
ap.add_argument("--tags", nargs="+", default=["v0", "v1", "v2"])
ap.add_argument("--runs", nargs="+", default=None,
                help="tag 순서대로 MLflow run 이름 (기본: v0_pretrained v1_finetune v2_aug_aihub, 그 뒤는 <tag>)")
ap.add_argument("--tracking-uri", default="sqlite:///mlflow.db")
ap.add_argument("--no-mlflow", action="store_true")
args = ap.parse_args()

DEFAULT_RUNS = {"v0": "v0_pretrained", "v1": "v1_finetune", "v2": "v2_aug_aihub"}
RUNS = dict(zip(args.tags, args.runs)) if args.runs else {t: DEFAULT_RUNS.get(t, t) for t in args.tags}
STAMP = time.strftime("%Y%m%d_%H%M")
OUT = os.path.join("portfolio_record", STAMP)
os.makedirs(OUT, exist_ok=True)


def folder(base, tag):                       # v0 은 접미사 없음 (results_ocr, results_real)
    return base if tag == "v0" else f"{base}_{tag}"


def field_acc(path):
    if not os.path.exists(path):
        return None
    rows = list(csv.DictReader(open(path, encoding="utf-8-sig")))
    if not rows:
        return None
    acc = {f: sum(r[f] == "exact" for r in rows) / len(rows) for f in ("date", "time", "name")}
    acc["all"] = sum(all(r[f] == "exact" for f in ("date", "time", "name")) for r in rows) / len(rows)
    acc["n"] = len(rows)
    return acc


def rec_metrics(tag):
    for p in (f"results_rec/{tag}/summary.txt", f"results_rec_with_v1/{tag}/summary.txt"):
        if os.path.exists(p):
            for line in open(p, encoding="utf-8"):
                if line.startswith("all"):
                    v = [float(x) for x in re.findall(r"([\d.]+)%", line)]
                    return dict(zip(("cer", "cer_ns", "wer", "line", "line_ns"), v))
    return None


def train_log(tag):
    p = f"train_{tag}.log"
    if not os.path.exists(p):
        return None
    steps, tr, val, best, last = [], [], [], None, 0
    for line in open(p, encoding="utf-8", errors="ignore"):
        m = re.search(r"global_step: (\d+).*? acc: ([\d.]+).*? loss: ([\d.]+)", line)
        if m:
            last = int(m.group(1))
            tr.append((last, float(m.group(2)), float(m.group(3))))
        m = re.search(r"cur metric, acc: ([\d.]+)", line)
        if m:
            val.append((last, float(m.group(1))))
        m = re.search(r"best metric, acc: ([\d.]+).*best_epoch: (\d+)", line)
        if m:
            best = (float(m.group(1)), int(m.group(2)))
    return {"log": p, "train": tr, "val": val, "best": best}


def pct(x):
    return "-" if x is None else f"{x * 100:.1f}%"


# ---------- 1) 수집 ----------
data = {}
for t in args.tags:
    data[t] = {"syn": field_acc(os.path.join(folder("results_ocr", t), "per_receipt.csv")),
               "real": field_acc(os.path.join(folder("results_real", t), "per_receipt.csv")),
               "rec": rec_metrics(t), "train": train_log(t)}

# ---------- 2) 비교표 ----------
hdr = ["버전", "MLflow run", "CER", "CER(공백무시)", "WER", "줄 정확도", "합성 날짜", "합성 시간", "합성 상호명",
       "실제 날짜", "실제 시간", "실제 상호명", "val 최고(세트 다름)"]
rows = []
for t in args.tags:
    d = data[t]
    rec, syn, real, tr = d["rec"] or {}, d["syn"] or {}, d["real"] or {}, d["train"] or {}
    best = tr.get("best") if tr else None
    rows.append([t, RUNS[t],
                 f"{rec['cer']:.2f}%" if rec else "-", f"{rec['cer_ns']:.2f}%" if rec else "-",
                 f"{rec['wer']:.2f}%" if rec else "-", f"{rec['line']:.2f}%" if rec else "-",
                 pct(syn.get("date")), pct(syn.get("time")), pct(syn.get("name")),
                 pct(real.get("date")), pct(real.get("time")), pct(real.get("name")),
                 f"{best[0] * 100:.2f}% (ep{best[1]})" if best else "-"])
with open(os.path.join(OUT, "compare.csv"), "w", encoding="utf-8-sig", newline="") as f:
    w = csv.writer(f)
    w.writerow(hdr)
    w.writerows(rows)
md = [f"# 버전별 비교 ({STAMP})", "", "| " + " | ".join(hdr) + " |", "|" + "---|" * len(hdr)]
md += ["| " + " | ".join(r) + " |" for r in rows]
md += ["", "- CER·WER·줄 정확도: 합성 test 11,590줄 (같은 세트)",
       "- 합성: 합성 영수증 200장, 실제: 실제 영수증 10장 (규칙 개발에 참고한 사진이라 독립 평가 아님)",
       "- val 최고는 버전마다 검증 세트가 달라 서로 비교하지 않는다"]

# 실제 영수증 상호명이 버전마다 다른 사례
per = {}
for t in args.tags:
    p = os.path.join(folder("results_real", t), "per_receipt.csv")
    if os.path.exists(p):
        for r in csv.DictReader(open(p, encoding="utf-8-sig")):
            per.setdefault(r["file"], {"gold": r["gold_name"]})[t] = (r["pred_name"], r["name"])
diff = [f for f, v in per.items() if len({v.get(t, ("", ""))[1] for t in args.tags if t in v}) > 1]
if diff:
    md += ["", "## 실제 영수증 상호명이 버전마다 다른 사례", "",
           "| 영수증 | 정답 | " + " | ".join(args.tags) + " |", "|" + "---|" * (len(args.tags) + 2)]
    for f in sorted(diff):
        v = per[f]
        md.append(f"| {f} | {v['gold']} | " + " | ".join(
            (f"{v[t][0]} ({'O' if v[t][1] == 'exact' else 'X'})" if t in v else "-") for t in args.tags) + " |")
open(os.path.join(OUT, "compare.md"), "w", encoding="utf-8").write("\n".join(md) + "\n")
print("\n".join(md))

# ---------- 3) 학습 곡선 ----------
try:
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt
    fig, ax = plt.subplots(1, 2, figsize=(12, 4))
    for t in args.tags:
        tr = data[t]["train"]
        if not tr:
            continue
        if tr["val"]:
            ax[0].plot([s for s, _ in tr["val"]], [a for _, a in tr["val"]], marker="o", label=t)
        if tr["train"]:
            ax[1].plot([s for s, _, _ in tr["train"]], [l for _, _, l in tr["train"]], label=t, alpha=0.8)
    ax[0].set_title("validation acc (val set differs by version)")
    ax[1].set_title("train loss")
    for a in ax:
        a.set_xlabel("step")
        a.legend()
        a.grid(alpha=0.3)
    plt.tight_layout()
    plt.savefig(os.path.join(OUT, "training_curves.png"), dpi=130)
    print("학습 곡선:", os.path.join(OUT, "training_curves.png"))
except Exception as e:
    print("[건너뜀] 학습 곡선 그래프:", e)

# ---------- 4) 실제 영수증 시각화 나란히 (마스킹본) ----------
try:
    from PIL import Image, ImageDraw, ImageFont
    fonts = [f for f in glob.glob(os.path.expanduser("~/fonts/**/*"), recursive=True) if f.lower().endswith((".ttf", ".otf", ".ttc"))]
    font = ImageFont.truetype(fonts[0], 40) if fonts else ImageFont.load_default()
    vdir = os.path.join(OUT, "side_by_side")
    os.makedirs(vdir, exist_ok=True)
    targets = sorted(diff) if diff else sorted(per)[:3]
    for f in targets:
        ims = [(t, os.path.join("vis", f"{t}_masked", f.rsplit(".", 1)[0] + "_vis.jpg")) for t in args.tags]
        ims = [(t, Image.open(p).convert("RGB")) for t, p in ims if os.path.exists(p)]
        if len(ims) < 2:
            continue
        H = max(i.height for _, i in ims)
        W = sum(i.width for _, i in ims) + 20 * (len(ims) - 1)
        c = Image.new("RGB", (W, H + 70), "white")
        x = 0
        for t, i in ims:
            ImageDraw.Draw(c).text((x + 10, 10), f"{t} ({RUNS[t]})", fill="black", font=font)
            c.paste(i, (x, 70))
            x += i.width + 20
        c.save(os.path.join(vdir, f.rsplit(".", 1)[0] + "_" + "_".join(args.tags) + ".jpg"), quality=85)
    print("나란히 비교:", vdir, f"({len(os.listdir(vdir))}장, 마스킹본)")
except Exception as e:
    print("[건너뜀] 나란히 비교 이미지:", e)

# ---------- 5) MLflow 보강 ----------
if not args.no_mlflow:
    try:
        import mlflow
        mlflow.set_tracking_uri(args.tracking_uri)
        mlflow.set_experiment("receipt-ocr")
        for t in args.tags:
            found = mlflow.search_runs(experiment_names=["receipt-ocr"], filter_string=f"tags.mlflow.runName = '{RUNS[t]}'")
            if not len(found):
                print(f"[MLflow] run 없음: {RUNS[t]}")
                continue
            run_id = found.iloc[0]["run_id"]
            client = mlflow.tracking.MlflowClient()
            already = set(client.get_run(run_id).data.metrics.keys())
            with mlflow.start_run(run_id=run_id):
                tr = data[t]["train"]
                if tr and "train_loss" not in already:       # 두 번 실행해도 곡선이 겹쳐 쌓이지 않게
                    for s, a, l in tr["train"]:
                        mlflow.log_metric("train_acc", a, step=s)
                        mlflow.log_metric("train_loss", l, step=s)
                    for s, a in tr["val"]:
                        mlflow.log_metric("val_acc", a, step=s)
                if tr and tr["best"]:
                    mlflow.log_metric("val_best_acc", tr["best"][0])
                    mlflow.log_metric("val_best_epoch", tr["best"][1])
                for p in (f"train_{t}.log", f"PaddleOCR/configs/receipt/rec_{t}.yml"):
                    if os.path.exists(p):
                        mlflow.log_artifact(p, artifact_path="training")
                if t == args.tags[-1]:
                    if os.path.exists("rec_data/aug_train.json"):
                        mlflow.log_artifact("rec_data/aug_train.json", artifact_path="training")
                    for p in ("compare.md", "compare.csv", "training_curves.png"):
                        if os.path.exists(os.path.join(OUT, p)):
                            mlflow.log_artifact(os.path.join(OUT, p), artifact_path="compare")
            print(f"[MLflow] {RUNS[t]} 기록 완료")
    except Exception as e:
        print("[MLflow] 기록 실패:", e)

# ---------- 6) 다운로드용 묶음 ----------
for t in args.tags:
    for src in (f"train_{t}.log", f"PaddleOCR/configs/receipt/rec_{t}.yml"):
        if os.path.exists(src):
            shutil.copy(src, OUT)
if os.path.exists("rec_data/aug_train.json"):
    shutil.copy("rec_data/aug_train.json", OUT)
for t in args.tags:
    for base in ("results_ocr", "results_real"):
        d = folder(base, t)
        for fn in ("summary.txt", "per_receipt.csv"):
            if os.path.exists(os.path.join(d, fn)):
                os.makedirs(os.path.join(OUT, "results", d), exist_ok=True)
                shutil.copy(os.path.join(d, fn), os.path.join(OUT, "results", d))
    for p in (f"results_rec/{t}/summary.txt",):
        if os.path.exists(p):
            os.makedirs(os.path.join(OUT, "results", f"results_rec_{t}"), exist_ok=True)
            shutil.copy(p, os.path.join(OUT, "results", f"results_rec_{t}"))
tar = os.path.expanduser(f"~/portfolio_record_{STAMP}.tar.gz")
with tarfile.open(tar, "w:gz") as tf:
    tf.add(OUT, arcname=f"portfolio_record_{STAMP}")
print(f"\n묶음: {tar} ({os.path.getsize(tar) / 1e6:.1f}MB) — OCR 원문(raw)은 넣지 않았습니다.")
