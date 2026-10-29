#!/usr/bin/env python3
"""현장 촬영 조건을 흉내 낸 줄 이미지 증강 (학습 데이터에만 적용).

실제 영수증 사진에서 생기는 문제를 골라 작은 비율로 적용한다.
  blur     : 가우시안 블러 (초점 흐림)
  motion   : 모션 블러 (손떨림)
  noise    : 가우시안 노이즈 (어두운 곳 센서 노이즈)
  dark     : 어둡게 + 대비 감소 (실내·야간)
  bright   : 밝게 + 날아감 (야외·플래시)
  shadow   : 한쪽에 그림자 (손·휴대폰 그림자)
  rotate   : ±3도 회전 (기울게 찍음)
  jpeg     : 강한 JPEG 압축 (메신저 전송 사진)
  fade     : 감열지 번짐·흐려짐 (글자 연하게 + 살짝 퍼짐)

원본은 그대로 두고 증강본을 따로 만들어 학습 목록에 추가한다(원본 + 증강본).
같은 --seed 면 같은 결과가 나온다. 설정은 aug_train.json 에 기록한다.

예) python augment_rec.py --data rec_data --lists train.txt aihub_train.txt --ratio 0.3 --seed 0
"""
from __future__ import annotations

import argparse
import json
import os
import random

import cv2
import numpy as np

OPS = ["blur", "motion", "noise", "dark", "bright", "shadow", "rotate", "jpeg", "fade"]

ap = argparse.ArgumentParser()
ap.add_argument("--data", default="rec_data")
ap.add_argument("--lists", nargs="+", default=["train.txt"], help="증강할 학습 목록 (val/test 는 넣지 않는다)")
ap.add_argument("--ratio", type=float, default=0.3, help="원본 중 증강본을 만들 비율 (작게 시작)")
ap.add_argument("--ops-per-image", type=int, default=2, help="한 증강본에 겹쳐 적용할 변환 수 (1~n 중 랜덤)")
ap.add_argument("--seed", type=int, default=0)
ap.add_argument("--out-name", default="aug_train")
args = ap.parse_args()


def apply(img, op, r):
    h, w = img.shape[:2]
    if op == "blur":
        k = r.choice([3, 5])
        return cv2.GaussianBlur(img, (k, k), r.uniform(0.6, 1.4))
    if op == "motion":
        k = r.choice([3, 5, 7])
        ker = np.zeros((k, k), np.float32)
        if r.random() < 0.5:
            ker[k // 2, :] = 1
        else:
            np.fill_diagonal(ker, 1)
        return cv2.filter2D(img, -1, ker / ker.sum())
    if op == "noise":
        n = np.random.default_rng(r.randrange(1 << 30)).normal(0, r.uniform(5, 15), img.shape)
        return np.clip(img.astype(np.float32) + n, 0, 255).astype(np.uint8)
    if op == "dark":
        a, b = r.uniform(0.45, 0.75), r.uniform(-30, 0)
        return np.clip(img.astype(np.float32) * a + b, 0, 255).astype(np.uint8)
    if op == "bright":
        a, b = r.uniform(1.1, 1.35), r.uniform(20, 50)
        return np.clip(img.astype(np.float32) * a + b, 0, 255).astype(np.uint8)
    if op == "shadow":
        x0 = r.randint(1, max(2, w - 1))
        mask = np.ones((h, w), np.float32)
        if r.random() < 0.5:
            mask[:, x0:] = r.uniform(0.5, 0.8)      # 오른쪽 그림자
        else:
            mask[:, :x0] = r.uniform(0.5, 0.8)      # 왼쪽 그림자
        mask = cv2.GaussianBlur(mask, (0, 0), max(1.0, w / 20))
        return np.clip(img.astype(np.float32) * mask[..., None], 0, 255).astype(np.uint8)
    if op == "rotate":
        ang = r.uniform(-3, 3)
        pad = int(abs(np.sin(np.radians(ang))) * w / 2) + 2     # 긴 줄도 위아래 글자가 잘리지 않게 여백
        img = cv2.copyMakeBorder(img, pad, pad, 0, 0, cv2.BORDER_REPLICATE)
        H2 = img.shape[0]
        M = cv2.getRotationMatrix2D((w / 2, H2 / 2), ang, 1.0)
        return cv2.warpAffine(img, M, (w, H2), borderMode=cv2.BORDER_REPLICATE)
    if op == "jpeg":
        ok, buf = cv2.imencode(".jpg", img, [cv2.IMWRITE_JPEG_QUALITY, r.randint(15, 40)])
        return cv2.imdecode(buf, cv2.IMREAD_COLOR) if ok else img
    if op == "fade":
        paper = np.full_like(img, 245)
        a = r.uniform(0.45, 0.7)
        out = cv2.addWeighted(img, a, paper, 1 - a, 0)
        return cv2.GaussianBlur(out, (3, 3), 0.8)
    return img


def main():
    r = random.Random(args.seed)
    items = []
    for lst in args.lists:
        items += [l.rstrip("\n").split("\t", 1) for l in open(os.path.join(args.data, lst), encoding="utf-8") if "\t" in l]
    pick = r.sample(items, int(len(items) * args.ratio))
    os.makedirs(os.path.join(args.data, args.out_name), exist_ok=True)
    out, used = [], {o: 0 for o in OPS}
    for i, (p, lab) in enumerate(pick):
        img = cv2.imread(os.path.join(args.data, p))
        if img is None:
            continue
        ops = r.sample(OPS, r.randint(1, args.ops_per_image))
        for op in ops:
            img = apply(img, op, r)
            used[op] += 1
        name = f"aug_{i:06d}_{'-'.join(ops)}.png"
        cv2.imwrite(os.path.join(args.data, args.out_name, name), img)
        out.append(f"{args.out_name}/{name}\t{lab}")
        if (i + 1) % 5000 == 0:
            print(f"{i + 1}/{len(pick)} 증강")
    with open(os.path.join(args.data, f"{args.out_name}.txt"), "w", encoding="utf-8") as f:
        f.write("\n".join(out) + "\n")
    cfg = {"source_lists": args.lists, "source_lines": len(items), "ratio": args.ratio, "aug_lines": len(out),
           "ops_per_image_max": args.ops_per_image, "seed": args.seed, "op_counts": used}
    json.dump(cfg, open(os.path.join(args.data, f"{args.out_name}.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=2)
    print(f"\n완료: 증강본 {len(out)}줄 → {args.data}/{args.out_name}.txt")
    print("변환별 사용 횟수:", used)


if __name__ == "__main__":
    main()
