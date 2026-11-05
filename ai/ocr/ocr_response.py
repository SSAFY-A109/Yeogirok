"""OCR 서버 응답 형식 (S15P31A109-51 합의안).

응답: {"status", "placeName", "date", "time"}
  - status: SUCCESS(세 값 모두 추출) / PARTIAL(일부만) / FAILED(하나도 없음)
  - placeName: string | null   (OCR 원문 상호명. 실제 장소 ID 매칭은 백엔드 API 담당)
  - date: "YYYY-MM-DD" | null
  - time: "HH:MM" | null
제외(합의): materialId(매칭은 백엔드 처리), missingFields(null 필드로 알 수 있음), visitedAt(date+time 중복),
           confidence(status와 중복), address(범위 밖), rawLines(OCR 원문·카드정보 노출 위험)
"""
from __future__ import annotations

FIELDS = ("placeName", "date", "time")


def to_response(result) -> dict:
    """parse_receipt 결과(ReceiptResult 또는 dict) → API 응답 dict. 원문 줄은 절대 넣지 않는다."""
    d = result.to_dict() if hasattr(result, "to_dict") else dict(result)
    out = {k: (d.get(k) or None) for k in FIELDS}
    found = sum(v is not None for v in out.values())
    status = "SUCCESS" if found == len(FIELDS) else ("FAILED" if found == 0 else "PARTIAL")
    return {"status": status, **out}
