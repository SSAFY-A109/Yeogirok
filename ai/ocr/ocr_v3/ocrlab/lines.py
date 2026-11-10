"""OCR 박스 → 줄 묶기. 기본(legacy)은 운영 receipt_parser.group_lines 를 그대로 쓴다.

실험 옵션 fields.line_group=v2 (11/09, dev3 줄 묶기 진단 근거)
  기존 방식의 문제 (results_v3/linediag_20261109.txt)
  - 박스 높이를 축에 맞춘 높이(max y - min y)로 잰다 → 기운 긴 박스는 실제 글자보다 훨씬 커 보인다
    (예: 10도 기운 박스 h 52, 실제 글자 높이 약 30). 줄 기준 높이로 '줄 안에서 가장 큰 높이'를 써서 위아래 줄까지 끌려온다
  - 기울기를 사진 전체 중앙값 하나로만 보정 → 한쪽만 휜·찢긴 영수증은 왼쪽 라벨과 오른쪽 값의 y 가 어긋난다
  - 지금 줄(마지막 줄) 하나와만 비교 → 기울어 줄이 엇갈리면 다른 줄로 붙는다
  v2
  - 박스 높이 = 왼쪽·오른쪽 변 길이 평균 (기울어도 글자 높이 그대로)
  - 같은 줄 판단: 그 줄에서 가장 넓은(기울기 믿을 만한) 박스의 기울기로 이 박스 위치의 y 를 예측해서 비교
  - 기준 높이 = 두 박스 중 작은 쪽 높이의 절반 (큰 박스가 이웃 줄을 끌어오지 않게)
  - 최근 줄 몇 개 중 가장 가까운 줄로 넣는다. 같은 x 에 이미 박스가 있으면(위아래로 쌓인 글자) 같은 줄로 보지 않는다
반환 형식은 group_lines 와 같다: [(줄 글자, 줄 최소 신뢰도)]
"""
from __future__ import annotations

import math
import statistics


def _dist(a, b) -> float:
    return math.hypot(a[0] - b[0], a[1] - b[1])


class _Tok:
    __slots__ = ("text", "score", "x0", "x1", "cx", "cy", "h", "w", "ang", "reliable")

    def __init__(self, text, score, poly):
        pts = [(float(p[0]), float(p[1])) for p in poly]
        xs, ys = [p[0] for p in pts], [p[1] for p in pts]
        self.text, self.score = str(text), float(score)
        self.x0, self.x1 = min(xs), max(xs)
        self.cx, self.cy = sum(xs) / len(xs), sum(ys) / len(ys)
        if len(pts) >= 4:
            self.w = (_dist(pts[0], pts[1]) + _dist(pts[3], pts[2])) / 2
            self.h = (_dist(pts[0], pts[3]) + _dist(pts[1], pts[2])) / 2
            dx, dy = pts[1][0] - pts[0][0], pts[1][1] - pts[0][1]
            self.ang = math.atan2(dy, dx) if dx else 0.0
        else:
            self.w, self.h, self.ang = max(xs) - min(xs), max(ys) - min(ys), 0.0
        if self.h <= 0:
            self.h = max(ys) - min(ys) or 1.0
        self.reliable = abs(self.ang) < 0.35 and self.w >= 2 * self.h


class _Line:
    def __init__(self, t: _Tok, skew: float):
        self.toks = [t]
        self.anchor = t
        self.skew = skew

    def slope(self) -> float:
        return self.anchor.ang if self.anchor.reliable else self.skew

    def y_at(self, x: float) -> float:
        return self.anchor.cy + math.tan(self.slope()) * (x - self.anchor.cx)

    def ref_h(self) -> float:
        return statistics.median(t.h for t in self.toks)

    def stacked(self, t: _Tok) -> bool:
        """같은 x 범위에 이미 박스가 있으면 위아래로 쌓인 것 (한 줄이 아님)"""
        for u in self.toks:
            ov = min(u.x1, t.x1) - max(u.x0, t.x0)
            if ov > 0.5 * min(u.x1 - u.x0, t.x1 - t.x0):
                return True
        return False

    def add(self, t: _Tok):
        self.toks.append(t)
        if t.reliable and (not self.anchor.reliable or t.w > self.anchor.w):
            self.anchor = t


def group_lines_v2(texts, scores, polys, recent: int = 4):
    toks = [_Tok(t, s, p) for t, s, p in zip(texts, scores, polys)]
    if not toks:
        return []
    rel = sorted(t.ang for t in toks if t.reliable)
    if not rel:
        rel = sorted(t.ang for t in toks if abs(t.ang) < 0.35) or [0.0]
    skew = rel[len(rel) // 2]
    tk = math.tan(skew)
    toks.sort(key=lambda t: t.cy - tk * t.cx)
    lines: list[_Line] = []
    for t in toks:
        best, best_r = None, 1.0
        for ln in lines[-recent:]:
            thr = 0.5 * min(ln.ref_h(), t.h)
            d = abs(t.cy - ln.y_at(t.cx))
            if d < thr and d / thr < best_r and not ln.stacked(t):
                best, best_r = ln, d / thr
        if best is None:
            lines.append(_Line(t, skew))
        else:
            best.add(t)
    lines.sort(key=lambda ln: statistics.median(u.cy - tk * u.cx for u in ln.toks))
    out = []
    for ln in lines:
        ln.toks.sort(key=lambda u: u.x0)
        out.append((" ".join(u.text for u in ln.toks).strip(), min(u.score for u in ln.toks)))
    return out


def make_lines(texts, scores, polys, cfg: dict | None = None):
    """설정에 따라 줄 묶기. 기본(legacy)은 운영과 같은 receipt_parser.group_lines"""
    mode = ((cfg or {}).get("fields") or {}).get("line_group", "legacy")
    if mode == "v2":
        return group_lines_v2(texts, scores, polys)
    from receipt_parser import boxes_from_polys, group_lines
    return group_lines(boxes_from_polys(texts, scores, polys))
