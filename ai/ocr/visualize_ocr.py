#!/usr/bin/env python3
"""OCR 결과 바운딩 박스 시각화 (포트폴리오·버전 비교용).

eval_real.py 가 저장한 raw JSON(인식 텍스트·점수·박스)을 읽어서 그린다. OCR을 다시 돌리지 않는다.
  - 회색 박스: 인식된 모든 글자 줄
  - 빨강: 상호명으로 뽑힌 줄 / 파랑: 날짜 / 초록: 시간
  - 오른쪽 패널: 정답 vs 추출 결과, 강조된 줄의 인식 텍스트
  - 마지막에 전체 이미지를 모은 grid.jpg 생성

예) python visualize_ocr.py --tag v0
    python visualize_ocr.py --tag v1 --raw results_real_v1/raw      # 파인튜닝 후 비교
"""
from __future__ import annotations

import argparse
import csv
import glob
import json
import os

import re

from PIL import Image, ImageDraw, ImageFont, ImageOps

from eval_baseline import judge
from receipt_parser import boxes_from_polys, extract_datetime, group_lines, parse_receipt

COLORS = {"name": (230, 40, 40), "date": (30, 100, 230), "time": (20, 160, 60), "other": (150, 150, 150)}
PANEL_W, OUT_H = 760, 1400


def load_font(size):
    d = os.path.expanduser(os.environ.get("RECEIPT_FONT_DIR", "~/fonts"))
    for pat in ("*Sans*Bold*", "*Sans*", "*"):
        for f in sorted(glob.glob(os.path.join(d, pat))):
            if f.lower().endswith((".ttc", ".otf", ".ttf")):
                try:
                    return ImageFont.truetype(f, size, index=1 if f.lower().endswith(".ttc") else 0)
                except Exception:
                    continue
    return ImageFont.load_default()


SENSITIVE_KW = ("대표", "표자", "사업자", "카드", "승인", "TEL", "Tel", "tel", "전화", "회원", "고객", "가맹점번호", "단말기",
                "계산원", "캐셔", "판매원", "판매사원", "담당", "서명")
PARTIAL_RE = re.compile(r"\d{3}-\d{2}-\d{5}|\d{2,4}-\d{3,4}-\d{4}")   # 남기는 줄 안의 사업자·전화번호 부분


def redact(t):
    return PARTIAL_RE.sub("***", t)
PHONE_RE = re.compile(r"\d{2,4}\s*[-)]\s*\d{3,4}\s*-\s*\d{4}")


def is_sensitive(text):
    """개인·결제 식별 정보가 들어 있을 수 있는 박스인지 (사업자·전화·카드·승인번호, 대표자 등)."""
    digits = re.sub(r"[\s\-.,]", "", text)
    if re.search(r"\d{6,}", digits) or PHONE_RE.search(text):
        return True
    if "*" in text and re.search(r"\d", text):          # 1234-56**-****-1234 같은 마스킹 카드번호
        return True
    return any(k in text for k in SENSITIVE_KW)


def mask_targets(texts, polys, keep):
    """마스킹할 박스 index. 같은 줄에 민감 정보가 있는 2~4글자 한글(대표자 이름)도 포함."""
    ys = []
    for p in polys:
        yy = [q[1] for q in p]
        ys.append((min(yy), max(yy)))
    sens = {i for i, t in enumerate(texts) if t.strip() and i not in keep and is_sensitive(t)}
    for i, t in enumerate(texts):
        if i in keep or i in sens or not re.fullmatch(r"[가-힣]{2,4}", t.strip()):
            continue
        a0, a1 = ys[i]
        for j in sens:
            b0, b1 = ys[j]
            if min(a1, b1) - max(a0, b0) > 0.5 * min(a1 - a0, b1 - b0):   # 같은 줄
                sens.add(i)
                break
    return sens


def squash(s):
    s = "".join((s or "").replace("_", " ").split()).lower()
    return re.sub(r"^(㈜|\((주|장|추|쥬)\)?)", "", s)        # ㈜ 오인식('(장' 등)과 무관하게 비교


def code_regions(img):
    """바코드·QR 영역(사각형 목록). 승인번호 등이 담겨 있어 함께 가린다."""
    try:
        import cv2
        import numpy as np
    except Exception:
        return []
    arr = cv2.cvtColor(np.array(img), cv2.COLOR_RGB2BGR)
    out = []
    dets = []
    if hasattr(cv2, "barcode_BarcodeDetector"):
        dets.append(cv2.barcode_BarcodeDetector())
    dets.append(cv2.QRCodeDetector())
    for det in dets:
        try:
            ok, pts = (det.detectMulti(arr) if hasattr(det, "detectMulti") else det.detect(arr))[:2]
        except Exception:
            continue
        if ok and pts is not None:
            for q in np.array(pts).reshape(-1, 4, 2):
                x0, y0, x1, y1 = q[:, 0].min(), q[:, 1].min(), q[:, 0].max(), q[:, 1].max()
                if not isinstance(det, cv2.QRCodeDetector):   # 바코드 검출 박스는 막대 가운데 띠만 잡아서 위아래로 넓힌다
                    h, w = y1 - y0, x1 - x0
                    ey = max(h, 0.12 * w); ex = 0.05 * w
                    x0, y0, x1, y1 = x0 - ex, y0 - ey, x1 + ex, y1 + ey + max(h, 0.15 * w)   # 아래 숫자까지
                out.append((x0, y0, x1, y1))
    return out


def roles(text, pred):
    """이 박스가 어떤 추출 결과에 쓰였는지."""
    out, t = [], squash(text)
    n = squash(pred.placeName)
    if n and len(t) >= 2 and (t in n or (n in t and not re.search(r"\d{5,}", PARTIAL_RE.sub("", t.replace(n, ""))))):
        out.append("name")      # '캐셔No:510001 상호명' 처럼 번호가 붙은 줄은 상호명 줄로 보지 않음
    d, tm, _ = extract_datetime([(text, 1.0)])
    if pred.date and d == pred.date:
        out.append("date")
    if pred.time and tm == pred.time:
        out.append("time")
    return out


def draw_one(img_path, raw, gold, tag, fonts, mask=False, no_group=False):
    img = ImageOps.exif_transpose(Image.open(img_path)).convert("RGB")   # cv2와 같은 방향으로 맞춤
    texts, scores, polys = raw["texts"], raw["scores"], raw["polys"]
    pred = parse_receipt(list(zip(texts, scores)) if no_group
                         else group_lines(boxes_from_polys(texts, scores, polys)))

    role_of = [roles(t, pred) if t.strip() else [] for t in texts]
    masked, masked_codes = set(), 0
    if mask:   # 날짜·시간·상호명으로 쓰인 박스는 남기고, 개인·결제 식별 정보는 회색으로 덮는다
        masked = mask_targets(texts, polys, keep={i for i, r in enumerate(role_of) if r})
        md = ImageDraw.Draw(img)
        pad = max(3, img.width // 300)
        rects = [(min(q[0] for q in polys[i]), min(q[1] for q in polys[i]),
                  max(q[0] for q in polys[i]), max(q[1] for q in polys[i])) for i in masked]
        for i, t in enumerate(texts):   # 상호명 줄 등 남기는 박스 안의 사업자·전화번호는 글자 위치 비율로 부분 마스킹
            if i in masked or not t:
                continue
            for m in PARTIAL_RE.finditer(t):
                x0, x1 = min(q[0] for q in polys[i]), max(q[0] for q in polys[i])
                y0, y1 = min(q[1] for q in polys[i]), max(q[1] for q in polys[i])
                cw = [1.0 if "가" <= ch <= "힣" else 0.55 for ch in t]   # 한글은 숫자보다 넓다
                tot, w = sum(cw), x1 - x0
                a, b = sum(cw[:m.start()]) / tot, sum(cw[:m.end()]) / tot
                rects.append((x0 + w * a - 0.03 * w, y0, x0 + w * b + 0.02 * w, y1))
        codes = code_regions(img)
        for x0, y0, x1, y1 in rects + codes:
            md.rectangle([x0 - pad, y0 - pad, x1 + pad, y1 + pad], fill=(70, 70, 70))
        masked_codes = len(codes)

    over = Image.new("RGBA", img.size, (0, 0, 0, 0))
    od = ImageDraw.Draw(over)
    lw = max(2, img.width // 400)
    hits = []
    for i, (t, s, p) in enumerate(zip(texts, scores, polys)):
        if not t.strip() or i in masked:
            continue
        r = role_of[i]
        key = r[0] if r else "other"
        c = COLORS[key]
        pts = [tuple(map(float, q)) for q in p]
        if r:
            od.polygon(pts, fill=c + (60,))
            hits.append((key, t, s))
        edge = COLORS["time"] if r == ["date", "time"] else c     # 날짜·시간이 한 박스: 채움 파랑, 테두리 초록
        od.line(pts + [pts[0]], fill=edge + (255,), width=lw * (2 if r else 1))
    img = Image.alpha_composite(img.convert("RGBA"), over).convert("RGB")

    scale = OUT_H / img.height
    img = img.resize((max(1, int(img.width * scale)), OUT_H))
    canvas = Image.new("RGB", (img.width + PANEL_W, OUT_H), "white")
    canvas.paste(img, (0, 0))
    d = ImageDraw.Draw(canvas)
    x, y = img.width + 30, 30
    f_title, f_body, f_small = fonts
    d.text((x, y), f"{os.path.basename(img_path)}  [{tag}]", font=f_title, fill="black"); y += 60

    results = []
    for key, label, pv, gv, kind in (("name", "상호명", pred.placeName, gold.get("merchant_name"), "merchant"),
                                     ("date", "날짜", pred.date, gold.get("date"), "date"),
                                     ("time", "시간", pred.time, (gold.get("time") or "")[:5] or None, "time")):
        j = judge(pv, gv or None, kind) if gold else "-"
        results.append(j)
        mark = {"exact": "O", "partial": "△", "wrong": "X", "miss": "X"}.get(j, "-")
        d.rectangle([x, y + 8, x + 22, y + 30], fill=COLORS[key])
        d.text((x + 34, y), f"{label}  [{mark}]", font=f_body, fill="black"); y += 40
        d.text((x + 34, y), f"추출: {pv or '(없음)'}", font=f_small, fill=COLORS[key]); y += 32
        if gold:
            d.text((x + 34, y), f"정답: {gv or '(없음)'}", font=f_small, fill=(90, 90, 90)); y += 32
        y += 16

    y += 10
    d.text((x, y), "강조된 줄 (인식 텍스트 / 신뢰도)", font=f_body, fill="black"); y += 44
    for key, t, s in hits[:12]:
        d.text((x, y), f"· {(redact(t) if mask else t)[:28]}  ({float(s):.2f})", font=f_small, fill=COLORS[key]); y += 32
    y = OUT_H - 50
    d.text((x, y), f"인식된 줄 {sum(1 for t in texts if t.strip())}개  (회색 = 기타 인식 줄)"
           + (f"  ·  개인정보 마스킹 {len(masked) + masked_codes}개" if mask else ""), font=f_small, fill=(110, 110, 110))
    return canvas, results, (len(masked), masked_codes)


def make_grid(paths, out_path, cols=5, thumb_h=700):
    ims = [Image.open(p) for p in paths]
    ims = [im.resize((int(im.width * thumb_h / im.height), thumb_h)) for im in ims]
    w = max(im.width for im in ims)
    rows = (len(ims) + cols - 1) // cols
    grid = Image.new("RGB", (w * cols, thumb_h * rows), "white")
    for i, im in enumerate(ims):
        grid.paste(im, ((i % cols) * w, (i // cols) * thumb_h))
    grid.save(out_path, quality=88)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--images", default="./real")
    ap.add_argument("--raw", default="results_real/raw")
    ap.add_argument("--labels", default="real_labels.csv")
    ap.add_argument("--tag", default="v0", help="모델 버전 이름 (그림 제목과 출력 폴더에 사용)")
    ap.add_argument("--out", default=None, help="기본: vis/<tag> (마스킹 시 vis/<tag>_masked)")
    ap.add_argument("--mask", action="store_true", help="사업자·전화·카드·승인번호, 대표자명 등을 가린다 (포트폴리오용)")
    ap.add_argument("--no-group", action="store_true", help="v00 재현: 줄 묶기 없이 추출")
    args = ap.parse_args()

    out = args.out or os.path.join("vis", args.tag + ("_masked" if args.mask else ""))
    os.makedirs(out, exist_ok=True)
    gold = {}
    if args.labels and os.path.exists(args.labels):
        gold = {r["file"]: r for r in csv.DictReader(open(args.labels, encoding="utf-8-sig"))}
    fonts = (load_font(34), load_font(28), load_font(24))

    saved, ok = [], 0
    for rj in sorted(glob.glob(os.path.join(args.raw, "*.json"))):
        fn = os.path.basename(rj)[:-5]                    # real_01.jpg.json -> real_01.jpg
        img_path = os.path.join(args.images, fn)
        if not os.path.exists(img_path):
            print(f"[건너뜀] 이미지 없음: {img_path}")
            continue
        canvas, res, nm = draw_one(img_path, json.load(open(rj, encoding="utf-8")), gold.get(fn, {}), args.tag, fonts,
                                   mask=args.mask, no_group=args.no_group)
        p = os.path.join(out, os.path.splitext(fn)[0] + "_vis.jpg")
        canvas.save(p, quality=90)
        saved.append(p)
        ok += all(r == "exact" for r in res)
        print(f"{p}  {res}" + (f"  마스킹 글자줄 {nm[0]}개, 바코드/QR {nm[1]}개" if args.mask else ""))
    if saved:
        make_grid(saved, os.path.join(out, "grid.jpg"))
        print(f"\n저장: {out}/  ({len(saved)}장, 세 항목 모두 정답 {ok}장)  + grid.jpg")


if __name__ == "__main__":
    main()
