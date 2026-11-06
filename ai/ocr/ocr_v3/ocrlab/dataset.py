"""파인튜닝 데이터 준비: 목록·라벨 검사, 영수증 단위 분할, 검출(det)·인식(rec) 학습 파일 내보내기.

목록 형식 (JSONL, 한 줄 = 사진 1장)
  {"image": "real_new/dev2/20261102_093452.jpg",   # --root 기준 상대 경로
   "receipt_id": "M1",                             # 같은 영수증(같은 가게 묶음)의 사진은 같은 값 → 분할이 갈리지 않음
   "label_source": "human_verified",               # human_verified | synthetic_generated | public_dataset | auto_candidate
   "lines": [{"text": "가나식당", "points": [[x,y],[x,y],[x,y],[x,y]], "ignore": false}],
   "split": null, "reviewer": null}
- auto_candidate(OCR 예측으로 만든 후보)는 정답이 아니다. 학습·검증·CER 에 쓰지 않고, 사람이 검수한 뒤 promote 로만 바꾼다
- 같은 영수증의 비슷한 사진이 train/val/test 에 섞이지 않게: receipt_id + 같은 파일(md5) + 비슷한 사진(dHash)을 한 묶음으로 나눈다
"""
from __future__ import annotations

import hashlib
import json
import math
import os
import statistics
from collections import Counter

import cv2
import numpy as np

SOURCES = ("human_verified", "synthetic_generated", "public_dataset", "auto_candidate")
TRAINABLE = ("human_verified", "synthetic_generated", "public_dataset")


def read_manifest(path: str) -> list[dict]:
    if not os.path.exists(path):
        raise FileNotFoundError(f"목록 파일이 없습니다: {path}")
    out = []
    for n, line in enumerate(open(path, encoding="utf-8"), 1):
        if line.strip():
            try:
                r = json.loads(line)
            except json.JSONDecodeError as e:
                raise ValueError(f"{path}:{n} JSON 형식 오류: {e}") from None
            r["_line"] = n
            out.append(r)
    return out


def write_manifest(records, path: str):
    os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        for r in records:
            f.write(json.dumps({k: v for k, v in r.items() if not k.startswith("_")}, ensure_ascii=False) + "\n")


def load_dict(path: str | None) -> list[str] | None:
    if not path:
        return None
    if not os.path.exists(path):
        raise FileNotFoundError(f"문자 사전이 없습니다: {path}")
    return [l.rstrip("\n") for l in open(path, encoding="utf-8")]


def _read_img(path):
    data = np.fromfile(path, np.uint8)
    return cv2.imdecode(data, cv2.IMREAD_COLOR | cv2.IMREAD_IGNORE_ORIENTATION) if data.size else None


def dhash(img, size=8) -> int:
    g = cv2.resize(cv2.cvtColor(img, cv2.COLOR_BGR2GRAY), (size + 1, size), interpolation=cv2.INTER_AREA)
    bits = (g[:, 1:] > g[:, :-1]).flatten()
    return int("".join("1" if b else "0" for b in bits), 2)


def poly_area(pts) -> float:
    a = 0.0
    for (x1, y1), (x2, y2) in zip(pts, pts[1:] + pts[:1]):
        a += x1 * y2 - x2 * y1
    return abs(a) / 2


def check_points(pts, w, h, tol=2.0) -> str | None:
    if not isinstance(pts, list) or len(pts) < 4:
        return "꼭짓점이 4개 미만"
    try:
        xy = [(float(p[0]), float(p[1])) for p in pts]
    except (TypeError, ValueError, IndexError):
        return "좌표가 숫자 [x, y] 형식이 아님"
    if any(not (math.isfinite(x) and math.isfinite(y)) for x, y in xy):
        return "좌표에 NaN/무한대"
    if any(x < -tol or y < -tol or x > w + tol or y > h + tol for x, y in xy):
        return f"이미지 밖 좌표 (이미지 {w}x{h})"
    if poly_area(xy) < 1.0:
        return "면적이 0에 가까움"
    return None


def check(records, root: str, dict_chars=None, max_len: int | None = None, near_dup: int = 6) -> dict:
    errors, warns = [], []
    cs = set(dict_chars or []) | {" "}
    oov = Counter()
    seen_md5, hashes = {}, []
    lens = []
    for r in records:
        where = f"{r.get('image')} (목록 {r['_line']}줄)"
        src = r.get("label_source")
        if src not in SOURCES:
            errors.append(f"{where}: label_source 가 {SOURCES} 중 하나가 아님: {src}")
        if not r.get("receipt_id"):
            errors.append(f"{where}: receipt_id 없음 (분할 누수 방지에 필요)")
        p = os.path.join(root, r.get("image") or "")
        if not r.get("image") or not os.path.isfile(p):
            errors.append(f"{where}: 이미지 파일 없음")
            continue
        raw = open(p, "rb").read()
        md5 = hashlib.md5(raw).hexdigest()
        img = _read_img(p)
        if img is None:
            errors.append(f"{where}: 이미지가 손상되어 읽을 수 없음")
            continue
        from .imageio import read_exif_orientation
        if read_exif_orientation(raw) not in (None, 1):
            warns.append(f"{where}: EXIF 회전 태그가 있음 → 읽는 프로그램마다 방향이 달라 좌표 기준이 모호. "
                         "candidates 가 만드는 회전 반영 사본(태그 없음)을 쓰세요")
        h, w = img.shape[:2]
        if md5 in seen_md5 and seen_md5[md5] != r.get("receipt_id"):
            warns.append(f"{where}: 같은 파일이 다른 receipt_id({seen_md5[md5]})에도 있음 → 분할 시 한 묶음으로 처리")
        seen_md5.setdefault(md5, r.get("receipt_id"))
        hashes.append((dhash(img), r.get("receipt_id"), r.get("image")))
        lines = r.get("lines")
        if not lines:
            (errors if src == "human_verified" else warns).append(f"{where}: 라벨 줄이 없음")
            continue
        for k, ln in enumerate(lines):
            bad = check_points(ln.get("points"), w, h)
            if bad:
                errors.append(f"{where} 줄{k}: {bad}")
            t = ln.get("text")
            if ln.get("ignore"):
                continue
            if not isinstance(t, str) or not t.strip():
                errors.append(f"{where} 줄{k}: 정답 텍스트가 비어 있음 (읽을 수 없는 줄은 ignore: true)")
                continue
            lens.append(len(t))
            if dict_chars:
                bad_c = [c for c in t if c not in cs]
                oov.update(bad_c)
            if max_len and len(t) > max_len:
                warns.append(f"{where} 줄{k}: {len(t)}자 > 최대 {max_len}자 (인식 학습에서 제외될 수 있음)")
    # 비슷한 사진이 서로 다른 receipt_id 에 있으면 경고
    for i in range(len(hashes)):
        for j in range(i + 1, len(hashes)):
            if hashes[i][1] != hashes[j][1] and bin(hashes[i][0] ^ hashes[j][0]).count("1") <= near_dup:
                warns.append(f"비슷한 사진이 다른 receipt_id: {hashes[i][2]} / {hashes[j][2]} → 분할 시 한 묶음으로 처리")
    if oov:
        warns.append(f"사전에 없는 글자 {sum(oov.values())}개 (종류 {len(oov)}): {''.join(c for c, _ in oov.most_common(30))}")
    return {"errors": errors, "warnings": warns, "stats": stats(records, lens)}


def stats(records, lens=None) -> dict:
    if lens is None:
        lens = [len(l.get("text") or "") for r in records for l in (r.get("lines") or []) if not l.get("ignore")]
    by_src = Counter(r.get("label_source") for r in records)
    by_split = Counter(r.get("split") or "미분할" for r in records)
    lines = sum(len(r.get("lines") or []) for r in records)
    return {"images": len(records), "receipts": len({r.get("receipt_id") for r in records}), "lines": lines,
            "chars": sum(lens), "by_source": dict(by_src), "by_split": dict(by_split),
            "text_len": ({"min": min(lens), "median": statistics.median(lens), "max": max(lens)} if lens else None)}


class _UF:
    def __init__(self):
        self.p = {}

    def find(self, x):
        self.p.setdefault(x, x)
        while self.p[x] != x:
            self.p[x] = self.p[self.p[x]]
            x = self.p[x]
        return x

    def union(self, a, b):
        self.p[self.find(a)] = self.find(b)


def group_keys(records, root: str, near_dup: int = 6) -> list[str]:
    """receipt_id 를 기본 묶음으로, 같은 파일·비슷한 사진이면 묶음을 합친다."""
    uf = _UF()
    info = []
    for r in records:
        rid = f"rid:{r['receipt_id']}"
        uf.find(rid)
        p = os.path.join(root, r["image"])
        md5 = hashlib.md5(open(p, "rb").read()).hexdigest()
        uf.union(f"md5:{md5}", rid)
        img = _read_img(p)
        info.append((rid, dhash(img) if img is not None else None))
    for i in range(len(info)):
        for j in range(i + 1, len(info)):
            a, b = info[i][1], info[j][1]
            if a is not None and b is not None and bin(a ^ b).count("1") <= near_dup:
                uf.union(info[i][0], info[j][0])
    return [uf.find(rid) for rid, _ in info]


def split(records, root: str, ratios=(0.8, 0.1, 0.1), seed: int = 0, near_dup: int = 6) -> list[dict]:
    if len(ratios) != 3 or abs(sum(ratios) - 1) > 1e-6:
        raise ValueError("ratios 는 train val test 세 값, 합 1")
    trainable = [r for r in records if r.get("label_source") in TRAINABLE]
    for r in records:
        if r.get("label_source") not in TRAINABLE:
            r["split"] = "review"                       # 검수 전 후보는 학습·평가에 쓰지 않음
    if not trainable:
        return records
    keys = group_keys(trainable, root, near_dup)
    groups = {}
    for r, k in zip(trainable, keys):
        groups.setdefault(k, []).append(r)
    order = sorted(groups, key=lambda k: hashlib.sha1(f"{seed}:{k}".encode()).hexdigest())
    total = len(trainable)
    bounds = (ratios[0] * total, (ratios[0] + ratios[1]) * total)
    done = 0
    for k in order:
        name = "train" if done < bounds[0] else "val" if done < bounds[1] else "test"
        for r in groups[k]:
            r["split"] = name
        done += len(groups[k])
    return records


def _allowed(r, sources, split_name):
    return r.get("label_source") in sources and (split_name is None or r.get("split") == split_name)


def export_det(records, root: str, out: str, sources=("human_verified",)) -> dict:
    """PaddleOCR 검출 학습 형식: '<이미지 경로>\\t[{"transcription": ..., "points": [[x,y],...]}]'"""
    _no_candidates(sources)
    os.makedirs(out, exist_ok=True)
    counts = {}
    for sp in ("train", "val", "test"):
        rows = []
        for r in records:
            if not _allowed(r, sources, sp) or not r.get("lines"):
                continue
            ann = [{"transcription": "###" if l.get("ignore") else l["text"],
                    "points": [[round(float(x), 1), round(float(y), 1)] for x, y in l["points"]]} for l in r["lines"]]
            rows.append(f"{r['image']}\t{json.dumps(ann, ensure_ascii=False)}")
        if rows:
            open(os.path.join(out, f"{sp}.txt"), "w", encoding="utf-8").write("\n".join(rows) + "\n")
        counts[sp] = len(rows)
    meta = {"task": "det", "data_dir": os.path.abspath(root), "sources": list(sources), "counts": counts}
    json.dump(meta, open(os.path.join(out, "export_meta.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=2)
    return meta


def _order4(pts):
    pts = np.array(pts, dtype=np.float32)
    s, d = pts.sum(1), np.diff(pts, axis=1).ravel()
    return np.array([pts[s.argmin()], pts[d.argmin()], pts[s.argmax()], pts[d.argmax()]], dtype=np.float32)


def crop_line(img, pts):
    """줄 영역 잘라내기 (PaddleOCR 의 회전 crop 과 같은 방식: 4점 원근 보정, 세로로 길면 90도 회전)."""
    if len(pts) == 4:
        q = _order4(pts)
    else:
        q = _order4(cv2.boxPoints(cv2.minAreaRect(np.array(pts, dtype=np.float32))))
    w = int(max(np.linalg.norm(q[0] - q[1]), np.linalg.norm(q[2] - q[3])))
    h = int(max(np.linalg.norm(q[0] - q[3]), np.linalg.norm(q[1] - q[2])))
    if w < 2 or h < 2:
        return None
    M = cv2.getPerspectiveTransform(q, np.array([[0, 0], [w, 0], [w, h], [0, h]], dtype=np.float32))
    c = cv2.warpPerspective(img, M, (w, h), borderMode=cv2.BORDER_REPLICATE, flags=cv2.INTER_CUBIC)
    if h / w >= 1.5:
        c = np.rot90(c)
    return c


def export_rec(records, root: str, out: str, sources=("human_verified",), dict_chars=None, max_len=None) -> dict:
    """PaddleOCR 인식 학습 형식: '<줄 이미지 경로>\\t<정답>'. 사전 밖 글자·최대 길이 초과 줄은 빼고 개수를 기록."""
    _no_candidates(sources)
    os.makedirs(out, exist_ok=True)
    cs = set(dict_chars or []) | {" "}
    counts, skipped = {}, Counter()
    for sp in ("train", "val", "test"):
        rows = []
        for r in records:
            if not _allowed(r, sources, sp) or not r.get("lines"):
                continue
            img = _read_img(os.path.join(root, r["image"]))
            if img is None:
                skipped["손상 이미지"] += 1
                continue
            stem = os.path.splitext(os.path.basename(r["image"]))[0]
            for k, l in enumerate(r["lines"]):
                t = l.get("text") or ""
                if l.get("ignore") or not t.strip():
                    skipped["ignore·빈 텍스트"] += 1
                    continue
                if dict_chars and any(c not in cs for c in t):
                    skipped["사전 밖 글자"] += 1
                    continue
                if max_len and len(t) > max_len:
                    skipped["최대 길이 초과"] += 1
                    continue
                c = crop_line(img, l["points"])
                if c is None:
                    skipped["너무 작은 영역"] += 1
                    continue
                rel = f"crops/{sp}/{r['receipt_id']}_{stem}_{k:03d}.png"
                os.makedirs(os.path.join(out, "crops", sp), exist_ok=True)
                cv2.imwrite(os.path.join(out, rel), c)
                rows.append(f"{rel}\t{t}")
        if rows:
            open(os.path.join(out, f"{sp}.txt"), "w", encoding="utf-8").write("\n".join(rows) + "\n")
        counts[sp] = len(rows)
    meta = {"task": "rec", "data_dir": os.path.abspath(out), "sources": list(sources), "counts": counts,
            "skipped": dict(skipped)}
    json.dump(meta, open(os.path.join(out, "export_meta.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=2)
    return meta


def _no_candidates(sources):
    if "auto_candidate" in sources:
        raise ValueError("auto_candidate(검수 전 OCR 후보)는 학습 데이터로 내보낼 수 없습니다. 검수 후 promote 하세요.")
    bad = [s for s in sources if s not in SOURCES]
    if bad:
        raise ValueError(f"알 수 없는 label_source: {bad}")


def import_ppocrlabel(label_txt: str, source: str, receipt_map: str | None = None) -> list[dict]:
    """PPOCRLabel 의 Label.txt ('경로\\t[{"transcription","points","difficult"}]') → 목록.
    receipt_map(CSV: image,receipt_id)이 없으면 사진마다 다른 receipt_id(파일 이름)."""
    if source not in SOURCES:
        raise ValueError(f"source 는 {SOURCES} 중 하나")
    rmap = {}
    if receipt_map:
        import csv
        rmap = {r["image"]: r["receipt_id"] for r in csv.DictReader(open(receipt_map, encoding="utf-8-sig"))}
    out = []
    for n, line in enumerate(open(label_txt, encoding="utf-8"), 1):
        if "\t" not in line:
            continue
        path, ann = line.rstrip("\n").split("\t", 1)
        items = json.loads(ann)
        lines = [{"text": "" if a.get("transcription") == "###" else a.get("transcription", ""),
                  "points": a.get("points"), "ignore": a.get("transcription") == "###" or bool(a.get("difficult"))}
                 for a in items]
        out.append({"image": path, "receipt_id": rmap.get(path) or os.path.splitext(os.path.basename(path))[0],
                    "label_source": source, "lines": lines, "split": None, "reviewer": None, "_line": n})
    return out


def promote(records, reviewer: str) -> tuple[list[dict], int]:
    """사람이 검수 표시(reviewed: true)한 후보만 human_verified 로 바꾼다. 나머지는 그대로 후보로 남긴다."""
    if not reviewer:
        raise ValueError("검수자 이름이 필요합니다 (--reviewer)")
    n = 0
    for r in records:
        if r.get("label_source") == "auto_candidate" and r.get("reviewed") is True:
            r["label_source"], r["reviewer"] = "human_verified", reviewer
            n += 1
    return records, n


def candidates_from_ocr(items) -> list[dict]:
    """OCR 예측으로 라벨 '후보'를 만든다. 정답이 아니므로 label_source=auto_candidate, reviewed=false.
    items: [(이미지 상대 경로, OcrOutput)] — 좌표는 그 이미지(회전 반영 사본) 기준"""
    out = []
    for rel, o in items:
        out.append({"image": rel, "receipt_id": os.path.splitext(os.path.basename(rel))[0],
                    "label_source": "auto_candidate", "reviewed": False, "split": None, "reviewer": None,
                    "lines": [{"text": t.text, "points": t.poly, "ignore": False, "ocr_score": round(t.score, 4)}
                              for t in o.tokens]})
    return out
