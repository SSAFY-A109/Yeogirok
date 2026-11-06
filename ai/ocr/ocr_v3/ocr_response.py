"""OCR 서버 응답 형식 (S15P31A109-51 → v3: 주소 추가, S15P31A109-95). 백엔드와 합의된 규격.

응답: {"status", "placeName", "date", "time", "address", "addressComplete"}
  - placeName: string | null   (영수증 표기 상호명. 실제 장소 ID 매칭은 백엔드 담당)
  - date: "YYYY-MM-DD" | null
  - time: "HH:mm" | null       (24시간)
  - address: string | null     (시·도 + 시·군·구 + 도로명까지. 지번만 있으면 시·도 + 시·군·구. 없으면 null)
  - addressComplete: boolean   (원문에서 그 주소의 건물번호까지 읽었으면 true. null 없음. 주소 실존 검증 아님)
  - status [확정]
      SUCCESS = 상호명·날짜·시간이 모두 있고 addressComplete 가 true
      FAILED  = 상호명·날짜·시간·주소 모두 없음      (11/02 규격 문서 기준)
      PARTIAL = 그 밖의 경우 (주소만 있음, 지번 주소만 있음, 주소가 덜 읽힘 포함)
제외(합의): materialId, missingFields, visitedAt, confidence, rawLines(OCR 원문·카드정보 노출 위험), 진단 정보
"""
from __future__ import annotations

FIELDS = ("placeName", "date", "time", "address")     # 응답에 내보내는 값
CORE = ("placeName", "date", "time")
KEYS = ("status",) + FIELDS + ("addressComplete",)     # 응답 키 순서


def decide_status(values: dict, address_complete: bool) -> str:
    core_found = sum(values.get(k) is not None for k in CORE)
    if core_found == len(CORE) and address_complete:
        return "SUCCESS"
    if core_found == 0 and values.get("address") is None:
        return "FAILED"
    return "PARTIAL"


def to_response(result) -> dict:
    """parse_receipt 결과(ReceiptResult 또는 dict) → API 응답 dict. 원문 줄·진단 정보는 절대 넣지 않는다."""
    d = result.to_dict() if hasattr(result, "to_dict") else dict(result)
    out = {k: (d.get(k) or None) for k in FIELDS}
    complete = d.get("addressComplete") is True and out["address"] is not None
    return {"status": decide_status(out, complete), **out, "addressComplete": complete}
