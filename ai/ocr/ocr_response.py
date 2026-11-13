"""OCR 서버 응답 형식 v3 (S15P31A109-95). status 규칙은 ocr_v3/ocr_response.py (11/02 규격 문서)와 같다.

응답: {"status", "placeName", "date", "time", "address", "addressComplete"}
  - status: SUCCESS = 상호명·날짜·시간이 모두 있고 addressComplete 가 true
            FAILED  = 상호명·날짜·시간·주소 4개 모두 없음
            PARTIAL = 그 밖 (하나라도 있음)
  - placeName: string | null   (OCR 원문 상호명. 실제 장소 ID 매칭은 백엔드 API 담당)
  - date: "YYYY-MM-DD" | null
  - time: "HH:MM" | null
  - address: string | null     (시·도 ~ 도로명. 건물번호를 읽었으면 건물번호까지. 예: "서울시 노원구 동일로 123")
                               지번만 있으면 시·도 + 시·군·구까지. 범위는 final_rules.ADDRESS_POLICY (OCR_ADDRESS_LEVEL)
  - addressComplete: bool      (도로명과 건물번호를 모두 읽었는지. address 가 null 이면 항상 false)
제외(합의): materialId(매칭은 백엔드 처리), missingFields(null 필드로 알 수 있음), visitedAt(date+time 중복),
           confidence(status와 중복), rawLines(OCR 원문·카드정보 노출 위험)
"""
from __future__ import annotations

FIELDS = ("placeName", "date", "time", "address")
CORE = ("placeName", "date", "time")
KEYS = ("status",) + FIELDS + ("addressComplete",)


def decide_status(values: dict, address_complete: bool) -> str:
    core_found = sum(values.get(k) is not None for k in CORE)
    if core_found == len(CORE) and address_complete:
        return "SUCCESS"
    if core_found == 0 and values.get("address") is None:
        return "FAILED"
    return "PARTIAL"


def to_response(result) -> dict:
    """parse_receipt·apply_final_rules 결과(ReceiptResult 또는 dict) → API 응답 dict. 원문 줄은 절대 넣지 않는다."""
    d = result.to_dict() if hasattr(result, "to_dict") else dict(result)
    out = {k: (d.get(k) or None) for k in FIELDS}
    complete = d.get("addressComplete") is True and out["address"] is not None
    return {"status": decide_status(out, complete), **out, "addressComplete": complete}
