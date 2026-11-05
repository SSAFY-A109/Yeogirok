#!/usr/bin/env python3
"""korean_PP-OCRv5_mobile_rec 파인튜닝 설정 파일 생성 + 사전 점검.

PaddleOCR 폴더 안에서 실행:
  cd ~/receipt_ocr/PaddleOCR && python ../make_train_config.py --tag v1
→ configs/receipt/rec_<tag>.yml 생성
"""
import argparse
import os

import yaml

ap = argparse.ArgumentParser()
ap.add_argument("--tag", default="v1")
ap.add_argument("--base", default="configs/rec/PP-OCRv5/multi_language/korean_PP-OCRv5_mobile_rec.yml")
ap.add_argument("--data", default="../rec_data")
ap.add_argument("--train-list", nargs="+", default=None, help="학습 목록 파일들 (기본: train.txt). 예: train.txt aihub_train.txt aug_train.txt")
ap.add_argument("--val-list", nargs="+", default=None, help="검증 목록 파일들 (기본: val.txt)")
ap.add_argument("--pretrained", default="../pretrained/korean_PP-OCRv5_mobile_rec_pretrained.pdparams")
ap.add_argument("--epochs", type=int, default=10)
ap.add_argument("--lr", type=float, default=0.0002)
ap.add_argument("--batch", type=int, default=256)
args = ap.parse_args()

cfg = yaml.safe_load(open(args.base, encoding="utf-8"))
G = cfg["Global"]
dict_path = G["character_dict_path"]

# 1) 사전 점검: 학습 사전 == 지금 쓰는 추론 모델 사전 (다르면 출력층이 안 맞음)
inf = os.path.expanduser("~/.paddlex/official_models/korean_PP-OCRv5_mobile_rec/inference.yml")
train_chars = [l.rstrip("\n") for l in open(dict_path, encoding="utf-8")]
if os.path.exists(inf):
    inf_chars = yaml.safe_load(open(inf, encoding="utf-8"))["PostProcess"]["character_dict"]
    print(f"사전 비교: 학습 {len(train_chars)}자 / 추론모델 {len(inf_chars)}자 → "
          f"{'동일' if train_chars == inf_chars else '다름 (확인 필요)'}")

# 2) 라벨 점검: 최대 길이 초과·사전 밖 글자
maxlen = G.get("max_text_length", 25)
cs = set(train_chars) | {" "}
train_files = [os.path.join(args.data, f) for f in (args.train_list or ["train.txt"])]
val_files = [os.path.join(args.data, f) for f in (args.val_list or ["val.txt"])]
for p in train_files + val_files:
    labels = [l.split("\t", 1)[1] for l in open(p, encoding="utf-8").read().splitlines() if "\t" in l]
    long_ = sum(len(t) > maxlen for t in labels)
    oov = sum(any(c not in cs for c in t) for t in labels)
    print(f"{os.path.basename(p)}: {len(labels)}줄, {maxlen}자 초과 {long_}줄(학습에서 자동 제외), 사전 밖 글자 포함 {oov}줄")

# 3) 설정 덮어쓰기
G.update({
    "pretrained_model": args.pretrained,
    "checkpoints": None,
    "epoch_num": args.epochs,
    "save_model_dir": f"./output/rec_{args.tag}",
    "save_epoch_step": 1,
    "eval_batch_step": [0, 500],
    "print_batch_step": 50,
    "use_visualdl": False,
    "distributed": False,
    "save_res_path": f"./output/rec_{args.tag}/predicts.txt",
})
opt = cfg["Optimizer"]
opt["lr"]["learning_rate"] = args.lr
opt["lr"]["warmup_epoch"] = 1

tr = cfg["Train"]
tr["dataset"]["data_dir"] = args.data
tr["dataset"]["label_file_list"] = train_files
tr["dataset"]["ratio_list"] = [1.0] * len(train_files)
tr["loader"]["batch_size_per_card"] = args.batch
if "sampler" in tr and tr["sampler"]:
    tr["sampler"]["first_bs"] = args.batch
tr["loader"]["num_workers"] = 8
ev = cfg["Eval"]
ev["dataset"]["data_dir"] = args.data
ev["dataset"]["label_file_list"] = val_files
ev["loader"]["batch_size_per_card"] = 256

os.makedirs("configs/receipt", exist_ok=True)
out = f"configs/receipt/rec_{args.tag}.yml"
yaml.safe_dump(cfg, open(out, "w", encoding="utf-8"), allow_unicode=True, sort_keys=False)
print(f"\n설정 저장: {out}")
print(f"  epochs={args.epochs}, lr={args.lr}, batch={args.batch}")
print(f"  train={train_files}")
print(f"  val={val_files}")
print(f"  pretrained={args.pretrained}")
