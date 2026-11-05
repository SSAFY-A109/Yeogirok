#!/usr/bin/env python3
"""AI Hub 'OCR 데이터(공공)' → 인식 모델(rec) 학습용 줄 이미지 + 정답 텍스트.

AI Hub 라벨은 단어 단위 박스라서, 같은 줄에 있는 가까운 단어를 이어 붙여
영수증 줄 조각과 비슷한 길이(최대 25자)의 이미지로 자른다.
페이지(문서 이미지) 단위로 train/val 을 나눠 같은 페이지가 양쪽에 섞이지 않게 한다.

출력 (make_rec_data.py 와 같은 형식, rec_data 와 합쳐 쓸 수 있음)
  <out>/aihub_train/xxx.png, <out>/aihub_val/xxx.png
  <out>/aihub_train.txt, <out>/aihub_val.txt   각 줄: "aihub_train/xxx.png\t정답"

예) python make_aihub_rec.py --src ~/aihub/x --out rec_data --max-lines 30000
"""
from __future__ import annotations

import argparse
import glob
import json
import os
import random

import cv2
import numpy as np

ap = argparse.ArgumentParser()
ap.add_argument("--src", required=True, help="jpg 와 json 이 풀려 있는 폴더 (하위 폴더 포함 검색)")
ap.add_argument("--out", default="rec_data")
ap.add_argument("--max-len", type=int, default=25, help="한 조각 최대 글자 수 (학습 설정 max_text_length 와 맞춤)")
ap.add_argument("--max-lines", type=int, default=30000, help="최대 조각 수 (합성 데이터보다 너무 많지 않게)")
ap.add_argument("--val-ratio", type=float, default=0.1)
ap.add_argument("--seed", type=int, default=0)
ap.add_argument("--dict", default=None, help="문자 사전. 기본: 한국어 인식 모델 inference.yml")
args = ap.parse_args()


def load_charset():
    import yaml
    p = args.dict or os.path.expanduser("~/.paddlex/official_models/korean_PP-OCRv5_mobile_rec/inference.yml")
    if p.endswith((".yml", ".yaml")) and os.path.exists(p):
        return set(yaml.safe_load(open(p, encoding="utf-8"))["PostProcess"]["character_dict"])
    if os.path.exists(p):
        return set(open(p, encoding="utf-8").read().split("\n"))
    print("[경고] 문자 사전을 못 찾아 사전 밖 글자 필터를 건너뜁니다.")
    return None


def segments(boxes):
    """단어 박스들을 줄로 묶고, 가까운 단어끼리 이어서 최대 max_len 글자 조각으로 만든다."""
    items = []
    for b in boxes:
        t = (b.get("data") or "").strip()
        if not t or b.get("type", 1) != 1:
            continue
        x0, x1, y0, y1 = min(b["x"]), max(b["x"]), min(b["y"]), max(b["y"])
        if x1 - x0 < 4 or y1 - y0 < 8:
            continue
        items.append((t, x0, y0, x1, y1))
    items.sort(key=lambda r: (r[2] + r[4]) / 2)
    lines = []
    for it in items:
        cy, h = (it[2] + it[4]) / 2, it[4] - it[2]
        if lines:
            L = lines[-1]
            lcy = sum((r[2] + r[4]) / 2 for r in L) / len(L)
            if abs(cy - lcy) < 0.5 * max(h, max(r[4] - r[2] for r in L)):
                L.append(it)
                continue
        lines.append([it])
    out = []
    for L in lines:
        L.sort(key=lambda r: r[1])
        cur = []
        for it in L:
            if cur:
                gap = it[1] - cur[-1][3]
                h = max(r[4] - r[2] for r in cur + [it])
                text = " ".join(r[0] for r in cur + [it])
                if gap > 1.2 * h or len(text) > args.max_len:
                    out.append(cur)
                    cur = []
            cur.append(it)
        if cur:
            out.append(cur)
    res = []
    for seg in out:
        text = " ".join(r[0] for r in seg)
        if len(text) > args.max_len:
            continue
        res.append((text, min(r[1] for r in seg), min(r[2] for r in seg), max(r[3] for r in seg), max(r[4] for r in seg)))
    return res


def main():
    rng = random.Random(args.seed)
    cs = load_charset()
    jsons = sorted(glob.glob(os.path.join(args.src, "**", "*.json"), recursive=True))
    pages = [j for j in jsons if os.path.exists(j[:-5] + ".jpg")]
    rng.shuffle(pages)
    n_val = max(1, int(len(pages) * args.val_ratio))
    split_of = {p: ("aihub_val" if i < n_val else "aihub_train") for i, p in enumerate(pages)}
    print(f"페이지 {len(pages)}장 (json+jpg 짝) → val {n_val} / train {len(pages) - n_val}")

    cand = []
    skipped_oov = 0
    for j in pages:
        boxes = json.load(open(j, encoding="utf-8")).get("Bbox", [])
        for seg in segments(boxes):
            if cs is not None and any(ch not in cs and ch != " " for ch in seg[0]):
                skipped_oov += 1
                continue
            cand.append((j, seg))
    rng.shuffle(cand)
    cand = cand[: args.max_lines]
    print(f"조각 후보 사용 {len(cand)}개 (사전 밖 글자로 제외 {skipped_oov}개)")

    by_page = {}
    for j, seg in cand:
        by_page.setdefault(j, []).append(seg)
    lines = {"aihub_train": [], "aihub_val": []}
    for s in lines:
        os.makedirs(os.path.join(args.out, s), exist_ok=True)
    for k, (j, segs) in enumerate(by_page.items(), 1):
        img = cv2.imread(j[:-5] + ".jpg")
        if img is None:
            continue
        H, W = img.shape[:2]
        split = split_of[j]
        stem = os.path.basename(j)[:-5]
        for n, (text, x0, y0, x1, y1) in enumerate(segs):
            px, py = rng.randint(2, 6), rng.randint(2, 5)
            crop = img[max(0, y0 - py):min(H, y1 + py), max(0, x0 - px):min(W, x1 + px)]
            if crop.size == 0:
                continue
            name = f"{stem}_{n:04d}.png"
            cv2.imwrite(os.path.join(args.out, split, name), crop)
            lines[split].append(f"{split}/{name}\t{text}")
        if k % 300 == 0:
            print(f"{k}/{len(by_page)} 페이지 처리")
    for s, ls in lines.items():
        with open(os.path.join(args.out, f"{s}.txt"), "w", encoding="utf-8") as f:
            f.write("\n".join(sorted(ls)) + "\n")
    print(f"\n완료: aihub_train {len(lines['aihub_train'])}줄 / aihub_val {len(lines['aihub_val'])}줄 → {args.out}/aihub_*.txt")


if __name__ == "__main__":
    main()
