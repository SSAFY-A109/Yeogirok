#!/usr/bin/env python3
"""영수증 영역 검출(YOLO 분할)용 배경 합성 데이터 생성.  (Jira S15P31A109-72)

합성 영수증 생성기(generate_receipts.py)로 영수증 원본을 그대로 만들고,
배경 위에 놓는 부분만 이 스크립트에서 새로 한다.
  - 영수증 크기: 사진 높이의 35~95%, 위치 무작위
  - 회전: 대부분 ±20°, 일부 90°·180° (돌아간 사진 대비)
  - 원근: 위에서 비스듬히 찍은 것처럼 네 꼭짓점을 흔듦
  - 배경: 코드로 생성(단색·어두운 책상·나무결·천·대리석·노트·그라데이션) → 외부 사진을 쓰지 않아 라이선스 문제 없음
  - 방해물: 다른 종이·휴대폰 같은 사각형, 손·물건 그림자
  - 촬영 효과: 노이즈, 밝기, 흐림, JPEG 압축
정답은 영수증 원본의 네 꼭짓점을 같은 변환 행렬로 옮긴 좌표라 사람이 그리지 않아도 정확하다.

출력 (Ultralytics YOLO 분할 형식)
  det_data/images/{train,val,test}/det_XXXXXX.jpg
  det_data/labels/{train,val,test}/det_XXXXXX.txt   각 줄: "0 x1 y1 x2 y2 x3 y3 x4 y4" (0~1 정규화)
  det_data/meta/{split}/det_XXXXXX.json              네 꼭짓점(원본 위쪽 왼쪽부터 시계방향), 회전 각도, 배경 종류
  det_data/data.yaml
  det_data/ocr_test/{images,labels}/                 test 사진 + 날짜·시간·상호명 정답 (eval_baseline.py로 OCR 전후 비교용)
  det_data/preview.jpg                               정답 윤곽을 그린 미리보기

분할은 영수증 단위(사진 1장 = 영수증 1장)이고, 시드 문자열이 OCR용 합성 데이터와 달라 기존 영수증과 겹치지 않는다.

예) python make_det_data.py --gen . --out det_data --n-train 1500 --n-val 200 --n-test 300 --workers 16
"""
from __future__ import annotations

import argparse
import json
import math
import os
import random
import sys
from multiprocessing import Pool

import cv2
import numpy as np

ap = argparse.ArgumentParser()
ap.add_argument("--gen", default=".", help="generate_receipts.py 가 있는 폴더")
ap.add_argument("--out", default="det_data")
ap.add_argument("--n-train", type=int, default=1500)
ap.add_argument("--n-val", type=int, default=200)
ap.add_argument("--n-test", type=int, default=300)
ap.add_argument("--long-side", type=int, default=1280, help="사진 긴 변 픽셀")
ap.add_argument("--rot90-ratio", type=float, default=0.15, help="90° 돌아간 사진 비율")
ap.add_argument("--rot180-ratio", type=float, default=0.05, help="180° 뒤집힌 사진 비율")
ap.add_argument("--seed", default="det-v1", help="시드 접두어 (같으면 같은 데이터)")
ap.add_argument("--workers", type=int, default=8)
args = ap.parse_args()

sys.path.insert(0, os.path.abspath(args.gen))
import generate_receipts as gr  # noqa: E402

BG_KINDS = ["plain_light", "plain_dark", "desk", "wood", "fabric", "marble", "notebook", "gradient"]


# ---------------- 배경 ----------------
def make_background(H, W, rng, nrng, kind):
    def color(lo, hi):
        return np.array([rng.uniform(lo, hi) for _ in range(3)], np.float32)

    if kind == "plain_light":
        bg = np.ones((H, W, 3), np.float32) * color(170, 245)
    elif kind == "plain_dark":
        bg = np.ones((H, W, 3), np.float32) * color(25, 110)
    elif kind == "desk":
        bg = np.ones((H, W, 3), np.float32) * color(60, 170)
        g = cv2.GaussianBlur(nrng.normal(0, 1, (H, W)).astype(np.float32), (0, 0), rng.uniform(15, 40))
        bg += (g / (np.abs(g).max() + 1e-6) * rng.uniform(10, 30))[..., None]
    elif kind == "wood":
        base = np.array([rng.uniform(60, 150), rng.uniform(90, 170), rng.uniform(130, 210)], np.float32)  # BGR 갈색 계열
        yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
        ang = rng.uniform(0, math.pi)
        t = xx * math.cos(ang) + yy * math.sin(ang)
        warp = cv2.GaussianBlur(nrng.normal(0, 1, (H, W)).astype(np.float32), (0, 0), 40) * 400
        grain = np.sin((t + warp) / rng.uniform(6, 14)) * 0.5 + 0.5
        bg = base * (0.75 + 0.35 * grain[..., None])
    elif kind == "fabric":
        base = color(40, 200)
        yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
        p = rng.uniform(3, 7)
        weave = (np.sin(xx / p) * np.sin(yy / p)) * rng.uniform(6, 14)
        bg = base + weave[..., None] + nrng.normal(0, 4, (H, W, 1)).astype(np.float32)
    elif kind == "marble":
        base = color(150, 235)
        v = cv2.GaussianBlur(nrng.normal(0, 1, (H, W)).astype(np.float32), (0, 0), rng.uniform(20, 50))
        veins = np.abs(np.sin(v * rng.uniform(25, 45)))
        bg = base * (0.85 + 0.15 * veins[..., None])
    elif kind == "notebook":
        bg = np.ones((H, W, 3), np.float32) * color(215, 250)
        step = rng.randint(28, 46)
        for y in range(rng.randint(0, step), H, step):
            bg[y:y + 2] -= rng.uniform(25, 45)
    else:  # gradient
        c1, c2 = color(30, 230), color(30, 230)
        ang = rng.uniform(0, 2 * math.pi)
        yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
        t = ((xx / W - 0.5) * math.cos(ang) + (yy / H - 0.5) * math.sin(ang)) + 0.5
        bg = c1 * (1 - t[..., None]) + c2 * t[..., None]
    return np.clip(bg, 0, 255)


def add_distractors(bg, rng, receipt_area):
    """영수증과 헷갈릴 만한 사각형(다른 종이, 휴대폰, 카드)을 몇 개 놓는다. 영수증보다 작게."""
    H, W = bg.shape[:2]
    for _ in range(rng.randint(0, 3)):
        kind = rng.choice(["paper", "phone", "card"])
        area = receipt_area * rng.uniform(0.05, 0.35)
        ar = {"paper": rng.uniform(0.6, 1.5), "phone": rng.uniform(0.45, 0.55), "card": rng.uniform(1.5, 1.7)}[kind]
        w = math.sqrt(area * ar)
        h = area / w
        cx, cy = rng.uniform(0, W), rng.uniform(0, H)
        rect = ((cx, cy), (w, h), rng.uniform(0, 180))
        pts = cv2.boxPoints(rect).astype(np.int32)
        col = {"paper": [rng.uniform(200, 250)] * 3, "phone": [rng.uniform(15, 50)] * 3,
               "card": [rng.uniform(40, 220) for _ in range(3)]}[kind]
        cv2.fillConvexPoly(bg, pts, col, lineType=cv2.LINE_AA)
    return bg


# ---------------- 영수증 원본 (생성기 그대로) ----------------
def render_receipt(rng):
    tid = rng.choice(sorted(gr.TEMPLATES))
    diff = rng.choice(["easy", "medium", "hard"])
    n_items = rng.randint(1, 8)
    tx = gr.make_transaction(tid, n_items, rng)
    ink_v = rng.randint(20, 60)
    ink = (ink_v, ink_v, ink_v + rng.randint(0, 25)) if rng.random() < 0.3 else (ink_v, ink_v, ink_v)
    r = gr.Renderer(paper=(rng.randint(248, 255),) * 3, ink=ink, rng=rng)
    r.mark()
    meta = gr.TEMPLATES[tid](r, tx)
    img, lines, keys = r.finish()
    arr = gr.paper_effects(img, keys, rng, diff)   # 용지 색·인쇄 흐림·구김
    fmt = "%Y-%m-%dT%H:%M:%S" if meta["has_seconds"] else "%Y-%m-%dT%H:%M"
    label = {"source_template_id": tid, "difficulty": diff, "merchant_name": meta["merchant_name"],
             "transaction_datetime": tx["dt"].strftime(fmt), "full_text": "\n".join(lines), "is_synthetic": True}
    return np.asarray(arr, np.float32), label


# ---------------- 배치 (회전·원근·크기·위치) ----------------
def place(h, w, rng, W, H):
    """영수증 원본(w×h)의 네 꼭짓점이 사진(W×H) 어디로 갈지 정한다. 반환: dst(4,2), 회전 각도"""
    src = np.float32([[0, 0], [w, 0], [w, h], [0, h]])   # 원본 위쪽 왼쪽부터 시계방향
    u = rng.random()
    if u < args.rot180_ratio:
        base = 180.0
    elif u < args.rot180_ratio + args.rot90_ratio:
        base = rng.choice([90.0, -90.0])
    else:
        base = 0.0
    ang = base + rng.uniform(-20, 20)
    # 원근: 위·아래 폭을 다르게(비스듬히 위에서 찍음) + 꼭짓점 흔들기
    k = rng.uniform(0, 0.18)
    top_shrink = k if rng.random() < 0.5 else -k
    pts = src.copy()
    pts[0, 0] += w * max(top_shrink, 0) / 2
    pts[1, 0] -= w * max(top_shrink, 0) / 2
    pts[3, 0] += w * max(-top_shrink, 0) / 2
    pts[2, 0] -= w * max(-top_shrink, 0) / 2
    j = 0.04 * min(w, h)
    pts += np.float32([[rng.uniform(-j, j), rng.uniform(-j, j)] for _ in range(4)])
    # 회전
    c, s = math.cos(math.radians(ang)), math.sin(math.radians(ang))
    cx, cy = w / 2, h / 2
    pts = np.float32([[cx + (x - cx) * c - (y - cy) * s, cy + (x - cx) * s + (y - cy) * c] for x, y in pts])
    # 크기: 회전된 영수증의 바운딩 박스가 사진 안 35~95%를 차지하도록
    bw, bh = np.ptp(pts[:, 0]), np.ptp(pts[:, 1])
    frac = rng.uniform(0.35, 0.95)
    scale = min(W * 0.96 / bw, H * 0.96 / bh) * frac
    pts = (pts - pts.min(0)) * scale
    bw, bh = pts[:, 0].max(), pts[:, 1].max()
    ox = rng.uniform(W * 0.02, max(W * 0.02, W * 0.98 - bw))
    oy = rng.uniform(H * 0.02, max(H * 0.02, H * 0.98 - bh))
    return pts + np.float32([ox, oy]), ang


def camera(img, rng, nrng):
    H, W = img.shape[:2]
    # 손·물건 그림자: 한쪽에서 들어오는 부드러운 어둠
    if rng.random() < 0.5:
        yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
        th = rng.uniform(0, 2 * math.pi)
        t = (xx / W - 0.5) * math.cos(th) + (yy / H - 0.5) * math.sin(th)
        sh = 1 - rng.uniform(0.2, 0.45) / (1 + np.exp(-(t - rng.uniform(-0.2, 0.3)) * rng.uniform(8, 25)))
        img = img * sh[..., None]
    img = img * rng.uniform(0.7, 1.15) + rng.uniform(-15, 15)               # 밝기
    img = img + nrng.normal(0, rng.uniform(1.5, 6), img.shape)               # 센서 노이즈
    if rng.random() < 0.7:
        img = cv2.GaussianBlur(img, (0, 0), rng.uniform(0.4, 1.6))           # 초점 흐림
    return np.clip(img, 0, 255).astype(np.uint8)


def make_one(job):
    split, i = job
    sid = f"{args.seed}-{split}-{i}"
    rng = random.Random(sid)
    nrng = np.random.default_rng(rng.randint(0, 2**32 - 1))
    portrait = rng.random() < 0.8
    L = args.long_side
    W, H = (int(L * rng.uniform(0.70, 0.80)), L) if portrait else (L, int(L * rng.uniform(0.70, 0.80)))

    rec, label = render_receipt(rng)
    h, w = rec.shape[:2]
    dst, ang = place(h, w, rng, W, H)
    src = np.float32([[0, 0], [w, 0], [w, h], [0, h]])
    M = cv2.getPerspectiveTransform(src, dst)

    kind = rng.choice(BG_KINDS)
    bg = make_background(H, W, rng, nrng, kind)
    bg = add_distractors(bg, rng, cv2.contourArea(dst))
    warped = cv2.warpPerspective(rec, M, (W, H), flags=cv2.INTER_AREA)
    mask = cv2.warpPerspective(np.ones((h, w), np.float32), M, (W, H), flags=cv2.INTER_LINEAR)[..., None]
    out = camera(warped * mask + bg * (1 - mask), rng, nrng)

    name = f"det_{split}_{i:06d}"
    ext = ".png" if split == "test" else ".jpg"   # test는 OCR 평가에도 쓰므로 무손실
    img_path = os.path.join(args.out, "images", split, name + ext)
    if ext == ".jpg":
        cv2.imwrite(img_path, out, [cv2.IMWRITE_JPEG_QUALITY, rng.randint(70, 95)])
    else:
        cv2.imwrite(img_path, out)
    poly = " ".join(f"{x / W:.6f} {y / H:.6f}" for x, y in dst)
    with open(os.path.join(args.out, "labels", split, name + ".txt"), "w") as f:
        f.write(f"0 {poly}\n")
    meta = {"id": name, "split": split, "size": [W, H], "corners_tl_tr_br_bl": dst.round(2).tolist(),
            "angle_deg": round(ang, 2), "background": kind, **label}
    json.dump(meta, open(os.path.join(args.out, "meta", split, name + ".json"), "w", encoding="utf-8"),
              ensure_ascii=False)
    if split == "test":   # eval_baseline.py 형식 (images/*.png + labels/*.json)
        cv2.imwrite(os.path.join(args.out, "ocr_test", "images", name + ".png"), out)
        lb = {"id": name, **label, "source_template_id": label["source_template_id"]}
        json.dump(lb, open(os.path.join(args.out, "ocr_test", "labels", name + ".json"), "w", encoding="utf-8"),
                  ensure_ascii=False)
    return split, kind, abs(round(ang / 90)) % 4


def preview(n=12):
    import glob
    files = sorted(glob.glob(os.path.join(args.out, "images", "val", "*")))[:n]
    tiles = []
    for p in files:
        im = cv2.imread(p)
        H, W = im.shape[:2]
        lab = os.path.join(args.out, "labels", "val", os.path.splitext(os.path.basename(p))[0] + ".txt")
        v = np.array(open(lab).read().split()[1:], np.float32).reshape(-1, 2) * [W, H]
        cv2.polylines(im, [v.astype(np.int32)], True, (0, 0, 255), 6)
        cv2.circle(im, tuple(v[0].astype(int)), 18, (0, 255, 0), -1)   # 초록 = 원본 위쪽 왼쪽 꼭짓점
        tiles.append(cv2.resize(im, (300, int(300 * H / W)))[:400].copy() if H / W * 300 >= 400
                     else cv2.copyMakeBorder(cv2.resize(im, (300, int(300 * H / W))), 0,
                                             400 - int(300 * H / W), 0, 0, cv2.BORDER_CONSTANT, value=(255, 255, 255)))
    if tiles:
        rows = [np.hstack(tiles[k:k + 6] + [np.full((400, 300, 3), 255, np.uint8)] * (6 - len(tiles[k:k + 6])))
                for k in range(0, len(tiles), 6)]
        cv2.imwrite(os.path.join(args.out, "preview.jpg"), np.vstack(rows))


def main():
    for sub in ("images", "labels", "meta"):
        for sp in ("train", "val", "test"):
            os.makedirs(os.path.join(args.out, sub, sp), exist_ok=True)
    os.makedirs(os.path.join(args.out, "ocr_test", "images"), exist_ok=True)
    os.makedirs(os.path.join(args.out, "ocr_test", "labels"), exist_ok=True)
    jobs = [("train", i) for i in range(args.n_train)] + [("val", i) for i in range(args.n_val)] + \
           [("test", i) for i in range(args.n_test)]
    stats = {}
    with Pool(args.workers) as pool:
        for k, (sp, kind, q) in enumerate(pool.imap_unordered(make_one, jobs, chunksize=4), 1):
            stats.setdefault(sp, {"n": 0, "bg": {}, "rot90_or_180": 0})
            stats[sp]["n"] += 1
            stats[sp]["bg"][kind] = stats[sp]["bg"].get(kind, 0) + 1
            stats[sp]["rot90_or_180"] += int(q != 0)
            if k % 200 == 0:
                print(f"{k}/{len(jobs)}")
    with open(os.path.join(args.out, "data.yaml"), "w") as f:
        f.write(f"path: {os.path.abspath(args.out)}\ntrain: images/train\nval: images/val\ntest: images/test\n"
                "names:\n  0: receipt\n")
    cfg = {"args": vars(args), "stats": stats}
    json.dump(cfg, open(os.path.join(args.out, "det_data_config.json"), "w", encoding="utf-8"),
              ensure_ascii=False, indent=2)
    preview()
    print(json.dumps(stats, ensure_ascii=False, indent=1))
    print(f"\n완료: {args.out}/data.yaml, 미리보기 {args.out}/preview.jpg")


if __name__ == "__main__":
    main()
