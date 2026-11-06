"""실험용 필드 추출(ocrlab.fields): 진단·여러 후보·유효성·정책. 가상 값."""
from ocrlab.fields import calendar_ok, datetime_candidates, extract
from ocrlab.settings import load_config


def L(*t):
    return [(x, 0.95) for x in t]


def test_baseline_matches_parse_receipt_and_spec():
    from receipt_parser import parse_receipt
    lines = L("가나마트 노원점", "서울시 노원구 동일로 123", "2026-10-20 12:30")
    resp, diag = extract(lines, load_config())
    r = parse_receipt(lines)
    assert (resp["placeName"], resp["date"], resp["time"]) == (r.placeName, r.date, r.time)
    assert resp == {"status": "SUCCESS", "placeName": "가나마트 노원점", "date": "2026-10-20", "time": "12:30",
                    "address": "서울시 노원구 동일로", "addressComplete": True}
    assert "diagnostics" not in resp and diag["address"]["selected_line"] == 1


def test_empty_ocr_failed():
    resp, diag = extract([], load_config())
    assert resp["status"] == "FAILED" and resp["addressComplete"] is False
    assert diag["datetime"]["candidates"] == []


def test_invalid_date_time_rejected():
    c = datetime_candidates(L("2026-13-40 25:61", "2026-10-20 24:00"))
    assert len(c) == 1 and c[0]["line"] == 1                 # 13월·40일·25시·61분 줄은 후보가 아님
    assert c[0]["dates"] == ["2026-10-20"] and c[0]["time"] is None   # 24:00 도 시간 아님
    resp, _ = extract(L("누리식당", "2026-13-40 25:61"), load_config())
    assert (resp["date"], resp["time"], resp["status"]) == (None, None, "PARTIAL")


def test_multiple_dates_ambiguity_flag():
    lines = L("차카식당", "2026-10-20", "2027-10-20", "12:30")
    resp, diag = extract(lines, load_config())
    assert resp["date"] == "2026-10-20"                     # legacy: 기존 규칙(먼저 나온 날짜)
    assert diag["datetime"]["ambiguous_date"] is True
    resp2, diag2 = extract(lines, load_config(sets=["fields.datetime_ambiguity=null_if_ambiguous"]))
    assert resp2["date"] is None and diag2["datetime"]["actions"]


def test_label_resolves_ambiguity():
    lines = L("차카식당", "유효기간 2027-10-20", "거래일시 2026-10-20 12:30")
    resp, diag = extract(lines, load_config(sets=["fields.datetime_ambiguity=null_if_ambiguous"]))
    assert resp["date"] == "2026-10-20" and diag["datetime"]["ambiguous_date"] is False


def test_calendar_validation_option():
    lines = L("차카식당", "2026-02-30 12:30")
    assert extract(lines, load_config())[0]["date"] == "2026-02-30"      # 기존 규칙은 일 1~31 만 확인
    assert extract(lines, load_config(sets=["fields.date_validation=calendar"]))[0]["date"] is None
    assert calendar_ok("2028-02-29") and not calendar_ok("2026-02-29")


def test_name_candidates_have_reasons():
    _, diag = extract(L("[영수증]", "가나마트 노원점", "(주)다라유통", "2026-10-20 12:30"), load_config())
    reasons = {c["value"]: c["reason"] for c in diag["placeName"]["candidates"]}
    assert reasons["가나마트 노원점"].startswith("지점명") and diag["placeName"]["selected"] == "가나마트 노원점"


def test_jibun_only_partial():
    resp, _ = extract(L("두리치킨", "서울시 노원구 공릉동 123-4", "2026-10-21 20:10"), load_config())
    assert (resp["address"], resp["addressComplete"], resp["status"]) == ("서울시 노원구", False, "PARTIAL")
