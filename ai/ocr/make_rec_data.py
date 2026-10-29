#!/usr/bin/env python3
"""인식 모델(rec) 파인튜닝용 줄 이미지 + 정답 텍스트 데이터 생성.

합성 영수증 생성기(generate_receipts.py)를 그대로 쓰되, 그릴 때 각 글자 조각의 위치를 기록하고
사진 변환(원근·회전) 행렬로 좌표를 옮겨서, 최종 이미지에서 글자 조각을 잘라낸다.
→ 정답 텍스트가 렌더링 데이터에서 오므로 OCR 결과에 의존하지 않는 '깨끗한 정답'이 된다.

분할 (영수증 단위로 나눠서 같은 영수증의 줄이 train/test 에 섞이지 않게 함)
  train : seed 1..N  (seed 당 200장)
  val   : seed 990
  test  : seed 20261027 → 지금까지 평가에 쓴 합성 200장과 '같은 영수증' (학습에 절대 사용 안 함)

출력 (PaddleOCR rec 학습 형식)
  rec_data/{train,val,test}/xxx.png
  rec_data/{train,val,test}.txt     각 줄: "train/xxx.png\t정답텍스트"

예) python make_rec_data.py --train-seeds 5 --workers 16
"""
from __future__ import annotations

import argparse
import glob
import os
import random
import re
import sys
from multiprocessing import Pool

import numpy as np

ap = argparse.ArgumentParser()
ap.add_argument("--gen", default=".", help="generate_receipts.py 가 있는 폴더")
ap.add_argument("--out", default="rec_data")
ap.add_argument("--train-seeds", type=int, default=5, help="학습용 seed 개수 (seed 당 영수증 200장)")
ap.add_argument("--val-seed", type=int, default=990)
ap.add_argument("--test-seed", type=int, default=20261027)
ap.add_argument("--workers", type=int, default=8)
ap.add_argument("--dict", default=None, help="문자 사전(txt, 한 줄에 한 글자). 기본: 모델 inference.yml 에서 자동")
args = ap.parse_args()

sys.path.insert(0, os.path.abspath(args.gen))
import cv2  # noqa: E402
import generate_receipts as G  # noqa: E402

SEP_RE = re.compile(r"^[\-=_*·.~\s]+$")   # 구분선(----, ====)은 제외


# ---- 생성기 패치: 글자 조각 위치 기록 -------------------------------------------------
def _line(self, segs, size=21, kind="mono", bold=False, gap=1.18, key=None,
          underline=False, invert=False, color=None, record=True):
    """G.Renderer.line 과 동일하게 그리고, 조각마다 (텍스트, 박스)를 self.segs 에 추가."""
    if not hasattr(self, "segs"):
        self.segs = []
    f = G.font(kind, size, bold)
    asc, desc = f.getmetrics()
    h = asc + desc
    placed = []
    for text, align, x in segs:
        if text == "":
            continue
        w = self.d.textlength(text, font=f)
        x0 = x if align == "l" else (x - w if align == "r" else x - w / 2)
        x0 = max(4, min(x0, G.W - 4 - w))
        if invert:
            self.d.rectangle([x0 - 4, self.y + 2, x0 + w + 4, self.y + h], fill=self.ink)
            self.d.text((x0, self.y), text, font=f, fill=self.paper)
        else:
            self.d.text((x0, self.y), text, font=f, fill=color or self.ink)
        placed.append((x0, text, (x0, self.y, x0 + w, self.y + h)))
        bb = self.d.textbbox((x0, self.y), text, font=f)          # 실제 잉크 영역
        self.segs.append((text, (bb[0], bb[1], bb[2], bb[3])))
    if underline and placed:
        self.d.line([(G.X0, self.y + h), (G.X1, self.y + h)], fill=self.ink, width=2)
    placed.sort(key=lambda p: p[0])
    if record and placed:
        self.lines.append(" ".join(p[1] for p in placed))
    if key and placed:
        bx = [p[2] for p in placed]
        box = (min(b[0] for b in bx), min(b[1] for b in bx), max(b[2] for b in bx), max(b[3] for b in bx))
        self.keys.setdefault(key, []).append(box)
    self.y += int(h * gap)


G.Renderer.line = _line
_STATE = {}
_orig_finish = G.Renderer.finish
_orig_gpt = G.cv2.getPerspectiveTransform


def _finish(self):
    _STATE["r"] = self
    return _orig_finish(self)


def _gpt(src, dst, *a, **k):
    M = _orig_gpt(src, dst, *a, **k)
    _STATE["M"] = M
    return M


G.Renderer.finish = _finish
G.cv2.getPerspectiveTransform = _gpt


# ---- 사전 ------------------------------------------------------------------------------
def load_charset():
    if args.dict:
        return set(open(args.dict, encoding="utf-8").read().split("\n"))
    for p in glob.glob(os.path.expanduser("~/.paddlex/official_models/korean_PP-OCRv5_mobile_rec/inference.yml")):
        import yaml
        y = yaml.safe_load(open(p, encoding="utf-8"))
        chars = y.get("PostProcess", {}).get("character_dict")
        if chars:
            return set(chars)
    print("[경고] 문자 사전을 못 찾아 사전 필터를 건너뜁니다.")
    return None


CHARSET = None


def crop_quad(img, quad, rng):
    """원근 변환된 사각형을 똑바로 펴서 자른다. 실제 검출 박스처럼 여백을 조금 랜덤하게 준다."""
    (tl, tr, br, bl) = quad
    w = int(max(np.linalg.norm(tr - tl), np.linalg.norm(br - bl)))
    h = int(max(np.linalg.norm(bl - tl), np.linalg.norm(br - tr)))
    if w < 8 or h < 8:
        return None
    px, py = rng.uniform(2, 6), rng.uniform(2, 5)
    ux, uy = (tr - tl) / max(1e-6, np.linalg.norm(tr - tl)), (bl - tl) / max(1e-6, np.linalg.norm(bl - tl))
    src = np.float32([tl - ux * px - uy * py, tr + ux * px - uy * py, br + ux * px + uy * py, bl - ux * px + uy * py])
    W2, H2 = int(w + 2 * px), int(h + 2 * py)
    dst = np.float32([[0, 0], [W2, 0], [W2, H2], [0, H2]])
    return cv2.warpPerspective(img, cv2.getPerspectiveTransform(src, dst), (W2, H2), flags=cv2.INTER_LINEAR,
                               borderMode=cv2.BORDER_REPLICATE)


def job(task):
    split, seed, i, tid, diff, n = task
    rng = random.Random(f"{seed}-{i}")
    img, lb, _ = G.build_one(i, tid, diff, n, rng)
    r, M = _STATE["r"], _STATE["M"]
    arr = np.array(img)
    crng = random.Random(f"crop-{seed}-{i}")
    out, skipped = [], 0
    for k, (text, (x0, y0, x1, y1)) in enumerate(r.segs):
        t = re.sub(r"\s+", " ", text).strip()     # 여러 칸 공백은 한 칸으로 (모델이 공백 개수까지 맞추긴 어려움)
        if not t or SEP_RE.match(t) or t == G.SYN_MARK:
            continue
        if CHARSET is not None and any(ch not in CHARSET and ch != " " for ch in t):
            skipped += 1
            continue
        quad = cv2.perspectiveTransform(np.float32([[[x0, y0], [x1, y0], [x1, y1], [x0, y1]]]), M)[0]
        c = crop_quad(arr, quad, crng)
        if c is None:
            continue
        name = f"s{seed}_{lb['id']}_{k:03d}.png"
        cv2.imwrite(os.path.join(args.out, split, name), cv2.cvtColor(c, cv2.COLOR_RGB2BGR))
        out.append(f"{split}/{name}\t{t}")
    return split, out, skipped, lb["id"], tid, diff


def plan_for(seed):
    """generate_receipts.main 과 같은 순서로 (템플릿, 난이도, 품목 수) 계획을 만든다 → test 는 기존 200장과 동일."""
    master = random.Random(seed)
    plan = []
    for tid in sorted(G.TEMPLATES):
        diffs, counts = G.DIFF_PLAN[:], G.ITEM_COUNT_PLAN[:]
        master.shuffle(diffs)
        master.shuffle(counts)
        plan += [(tid, d, n) for d, n in zip(diffs, counts)]
    master.shuffle(plan)
    return plan


def init_worker(cs):
    global CHARSET
    CHARSET = cs


def main():
    cs = load_charset()
    splits = {"train": list(range(1, args.train_seeds + 1)), "val": [args.val_seed], "test": [args.test_seed]}
    tasks = []
    for split, seeds in splits.items():
        os.makedirs(os.path.join(args.out, split), exist_ok=True)
        for seed in seeds:
            tasks += [(split, seed, i, tid, d, n) for i, (tid, d, n) in enumerate(plan_for(seed), start=1)]
    lines = {s: [] for s in splits}
    skipped = 0
    with Pool(args.workers, initializer=init_worker, initargs=(cs,)) as pool:
        for j, (split, out, sk, *_rest) in enumerate(pool.imap_unordered(job, tasks, chunksize=4), 1):
            lines[split] += out
            skipped += sk
            if j % 100 == 0:
                print(f"{j}/{len(tasks)} 영수증 처리")
    for split, ls in lines.items():
        ls.sort()
        with open(os.path.join(args.out, f"{split}.txt"), "w", encoding="utf-8") as f:
            f.write("\n".join(ls) + "\n")
    chars = sorted({ch for ls in lines.values() for l in ls for ch in l.split("\t", 1)[1]})
    print(f"\n완료: train {len(lines['train'])}줄 / val {len(lines['val'])}줄 / test {len(lines['test'])}줄")
    print(f"사전 밖 글자로 제외된 조각: {skipped}개   사용된 문자 종류: {len(chars)}개")
    print(f"라벨 파일: {args.out}/train.txt, val.txt, test.txt")


if __name__ == "__main__":
    main()
