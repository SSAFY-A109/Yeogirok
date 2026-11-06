"""사진 방향 보정 (실험 옵션 pre.orient_retry=auto, 기본 legacy = 운영과 같음, 새 모델 없음).

근거: 11/05 dev2(B30) 상호명 진단에서 틀린 17장 중 7장이 사진 방향 문제
  - 영수증이 뒤집힌 사진: 글자가 깨져 영수증에 늘 있는 말(합계·금액·카드·승인 …)이 거의 안 나옴
  - 옆으로 누운 사진: 글자는 읽히지만 상자가 세로로 길어 줄 묶기가 한 줄로 뭉침
방법: 첫 OCR 결과가 위 둘 중 하나로 보일 때만 180·90·270도로 돌려 다시 읽고, 영수증 단어가 가장 많이 나온 방향을 고른다.
정상으로 보이는 사진은 다시 읽지 않는다 (속도 그대로). 원래 방향보다 영수증 단어가 더 많을 때만 바꾼다.
"""
from __future__ import annotations

import re

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


def run(engine, img, first):
    """first: 원래 방향 OCR 결과. (고른 결과, 메타)"""
    retry, why = needs_retry(first)
    meta = {"triggered": retry, "reason": why, "angle": 0, "tried": {}}
    if not retry:
        return first, meta
    q0 = quality(first)
    best, best_q = first, q0
    meta["tried"][0] = list(q0)
    for angle in (180, 90, 270):
        out = engine.run(rotate(img, angle))
        q = quality(out)
        meta["tried"][angle] = list(q)
        # 원래보다 점수 첫째 값(영수증 단어 − 누움 벌점)이 커야 하고, 영수증 단어가 MIN_HITS 이상이어야 바꾼다
        if q > best_q and q[0] > q0[0] and keyword_hits([t.text for t in out.tokens]) >= MIN_HITS:
            best, best_q, meta["angle"] = out, q, angle
    return best, meta
