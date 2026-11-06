"""틀린 필드의 원인 나누기 → 개선 방향 (classify_errors.py 와 같은 규칙, 주소 필드용).

  선택·파싱  : 정답이 OCR 글자 안에 그대로 있음        → 후처리 개선
  인식 오류  : 아주 비슷한 글자열이 있음 (CER ≤ 0.34)  → 인식 모델 학습 검토 (+ 전처리)
  검출 누락 또는 심한 오인식 : 비슷한 글자열도 없음      → 탐지 모델 학습 검토 (그림으로 확인 후)
  오검출     : 정답은 없음인데 값을 냄                  → 후처리 개선
상호명·날짜·시간은 기존 classify_errors.py 로 판정한다 (eval --save-raw 결과 폴더를 그대로 읽음).
출력에는 판정만 쓰고 원문·정답·예측 문자열은 쓰지 않는다.
"""
from __future__ import annotations

ACTION = {"선택·파싱": "후처리 개선", "인식 오류": "인식(rec) 학습 검토", "검출 누락 또는 심한 오인식": "탐지(det) 학습 검토",
          "오검출": "후처리 개선", "정답": "-", "정답 없음": "-"}


def norm(s: str) -> str:
    return "".join((s or "").split())


def semiglobal_cer(g: str, t: str) -> float:
    """t 안의 어떤 부분 문자열과 g 를 맞췄을 때 최소 편집거리 / len(g)."""
    if not g:
        return 0.0
    if not t:
        return 1.0
    prev = [0] * (len(t) + 1)
    for i in range(1, len(g) + 1):
        cur = [i] + [0] * len(t)
        for j in range(1, len(t) + 1):
            cur[j] = min(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + (g[i - 1] != t[j - 1]))
        prev = cur
    return min(prev) / len(g)


def classify(judge: str, gold, ocr_texts: list[str], cer_max: float = 0.34) -> tuple[str, float | None]:
    if judge in ("exact", "null_ok"):
        return ("정답" if judge == "exact" else "정답 없음"), None
    if gold is None:
        return "오검출", None
    joined = norm(" ".join(ocr_texts))
    g = norm(gold)
    if g and g in joined:
        return "선택·파싱", 0.0
    c = semiglobal_cer(g, joined)
    return ("인식 오류" if c <= cer_max else "검출 누락 또는 심한 오인식"), round(c, 3)
