"""운영 사진 방향 보정 (S15P31A109-49). 첫 OCR 결과가 깨져 보이거나(영수증 단어 2개 미만) 글자 상자가
세로로 누워 있으면 사진을 돌려 다시 읽는다. 맞을 듯한 방향부터 읽고 충분히 읽히면 멈춘다 (fast). 새 모델 없음.

아래 '옮긴 코드' 부분은 ocr_v3/ocrlab/orient.py (md5 08b3c618, 실험 E14·E15 에서 쓴 코드)에서 그대로 옮겼다.
운영 OCR 함수(eval_baseline.run_ocr_factory 가 돌려주는 (texts, scores, polys))에 맞추는 부분만 새로 썼다.
"""
from __future__ import annotations

import re


class _Tok:
    __slots__ = ("text", "score", "poly")

    def __init__(self, text, score, poly):
        self.text, self.score, self.poly = text, score, poly


class _Out:
    """ocr_v3 engine.convert_result 와 같은 변환 (글자 str, 점수 float, 좌표 float)."""
    def __init__(self, texts, scores, polys):
        texts, scores, polys = list(texts), list(scores), list(polys)
        if not (len(texts) == len(scores) == len(polys)):
            raise ValueError(f"OCR 결과 길이가 맞지 않음: texts {len(texts)}, scores {len(scores)}, polys {len(polys)}")
        self.tokens = [_Tok(str(t), float(s), [[float(x), float(y)] for x, y in p]) for t, s, p in zip(texts, scores, polys)]

    def as_triplet(self):
        return [t.text for t in self.tokens], [t.score for t in self.tokens], [t.poly for t in self.tokens]


class _Engine:
    def __init__(self, runner):
        self.runner = runner

    def run(self, img):
        return _Out(*self.runner(img))


def read_with_orientation(runner, img, mode: str = "fast"):
    """runner: 사진 → (texts, scores, polys). mode: fast(기본) | auto | off.
    반환 (texts, scores, polys, meta). meta: triggered·reason·angle·reads"""
    engine = _Engine(runner)
    first = engine.run(img)
    if mode not in ("auto", "fast"):
        return (*first.as_triplet(), {"triggered": False, "reason": "", "angle": 0, "tried": {}, "reads": 0})
    best, meta = run(engine, img, first, mode)
    return (*best.as_triplet(), meta)


# ---------------- 옮긴 코드 (ocr_v3/ocrlab/orient.py) ----------------

KEYWORDS = ("합계", "금액", "카드", "승인", "영수증", "부가세", "상품", "단가", "수량", "일시", "번호", "주소",
            "대표", "사업자", "판매", "매출", "결제", "거래", "가맹점", "전화", "과세", "할부", "일시불")


MIN_HITS = 2           # 이보다 적으면 '깨져 보임'


VERTICAL_RATIO = 0.5   # 두 글자 이상 상자 중 세로로 긴(높이 ≥ 1.5×너비) 비율이 이 이상이면 '옆으로 누움'


def keyword_hits(texts) -> int:
    flat = [re.sub(r"\s+", "", t or "") for t in texts]
    return sum(any(k in t for k in KEYWORDS) for t in flat)


def vertical_ratio(tokens) -> float:
    big = []
    for t in tokens:
        if len((t.text or "").strip()) < 2:
            continue
        xs = [p[0] for p in t.poly]
        ys = [p[1] for p in t.poly]
        w, h = max(xs) - min(xs), max(ys) - min(ys)
        big.append(h >= 1.5 * max(w, 1e-6))
    return sum(big) / len(big) if big else 0.0


def quality(out) -> tuple:
    """(영수증 단어 수 − 옆으로 누운 배치 벌점 5, 평균 신뢰도). 클수록 바른 방향."""
    scores = [t.score for t in out.tokens]
    hits = keyword_hits([t.text for t in out.tokens])
    lying = vertical_ratio(out.tokens) >= VERTICAL_RATIO
    return (hits - (5 if lying else 0), round(sum(scores) / len(scores), 4) if scores else 0.0)


def needs_retry(out) -> tuple[bool, str]:
    hits = keyword_hits([t.text for t in out.tokens])
    vr = vertical_ratio(out.tokens)
    if vr >= VERTICAL_RATIO:
        return True, f"세로 상자 비율 {vr:.2f}"
    if hits < MIN_HITS:
        return True, f"영수증 단어 {hits}개"
    return False, ""


def rotate(img, angle: int):
    import cv2
    code = {90: cv2.ROTATE_90_CLOCKWISE, 180: cv2.ROTATE_180, 270: cv2.ROTATE_90_COUNTERCLOCKWISE}[angle]
    return cv2.rotate(img, code)


GOOD_HITS = 4          # fast: 다시 읽은 결과가 깨지지 않았고 영수증 단어가 이 이상이면 나머지 방향은 건너뜀


def order_for(first, reason: str) -> tuple:
    """fast 에서 먼저 읽을 방향.
    - 뒤집힌 듯(영수증 단어 부족): 180도 먼저
    - 옆으로 누움(세로 상자): PaddleOCR 은 세로로 긴 글자 조각을 반시계로 90도 돌려 읽는다.
      그래서 첫 결과에서 영수증 단어가 읽혔으면 사진도 반시계로 돌리는 270도가 맞을 가능성이 높고, 아니면 90도 먼저"""
    if reason.startswith("세로"):
        return (270, 90, 180) if keyword_hits([t.text for t in first.tokens]) >= MIN_HITS else (90, 270, 180)
    return (180, 90, 270)


def good_enough(out) -> bool:
    return not needs_retry(out)[0] and keyword_hits([t.text for t in out.tokens]) >= GOOD_HITS


def run(engine, img, first, mode: str = "auto"):
    """first: 원래 방향 OCR 결과. mode: auto(세 방향 모두 읽음) | fast(맞을 듯한 방향부터, 충분하면 멈춤).
    (고른 결과, 메타). 메타의 reads = 다시 읽은 횟수"""
    retry, why = needs_retry(first)
    meta = {"triggered": retry, "reason": why, "angle": 0, "tried": {}, "reads": 0}
    if not retry:
        return first, meta
    q0 = quality(first)
    best, best_q = first, q0
    meta["tried"][0] = list(q0)
    for angle in (order_for(first, why) if mode == "fast" else (180, 90, 270)):
        out = engine.run(rotate(img, angle))
        meta["reads"] += 1
        q = quality(out)
        meta["tried"][angle] = list(q)
        # 원래보다 점수 첫째 값(영수증 단어 − 누움 벌점)이 커야 하고, 영수증 단어가 MIN_HITS 이상이어야 바꾼다
        if q > best_q and q[0] > q0[0] and keyword_hits([t.text for t in out.tokens]) >= MIN_HITS:
            best, best_q, meta["angle"] = out, q, angle
            if mode == "fast" and good_enough(out):
                break
    return best, meta

