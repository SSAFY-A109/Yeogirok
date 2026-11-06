"""날짜 자리 혼동 문자 보정 (fields.date_lookalike_fix). 가상 영수증 값만 쓴다.
- 기본값(legacy)은 운영과 같아야 한다
- fallback 은 날짜를 못 찾았을 때만 동작하고, 이미 찾은 날짜·상호명·주소는 바꾸지 않는다
- 날짜 모양이 아닌 곳(전화번호·코드·상호명)은 바꾸지 않는다"""
import json
import os

from ocrlab.fields import extract, fix_date_lookalikes
from ocrlab.settings import load_config

ON = ["fields.date_lookalike_fix=fallback"]


def L(*t):
    return [(x, 0.95) for x in t]


def test_default_is_legacy_and_same_as_parse_receipt():
    from receipt_parser import parse_receipt
    cfg = load_config()
    assert cfg["fields"]["date_lookalike_fix"] == "legacy" and cfg["_changed"] == {}
    lines = L("가나식당", "판매일:26-1C-01 18:26,목요일")
    resp, _ = extract(lines, cfg)
    r = parse_receipt(lines)
    assert (resp["date"], resp["time"]) == (r.date, r.time) == (None, "18:26")   # 운영 규칙: C 때문에 날짜 누락


def test_fallback_fixes_faded_zero_read_as_c():
    resp, diag = extract(L("가나식당", "판매일:26-1C-01 18:26,목요일"), load_config(sets=ON))
    assert (resp["date"], resp["time"]) == ("2026-10-01", "18:26")
    assert any("혼동 문자" in a for a in diag["datetime"]["actions"])


def test_fallback_glued_time_also_recovered():
    # 날짜와 시간이 붙은 줄: C 때문에 기존 '붙은 날짜·시간 분리'도 안 돼서 시간까지 누락되던 경우
    lines = L("가나식당", "판매일:26-1C-0118:26")
    assert extract(lines, load_config())[0]["time"] is None
    resp, _ = extract(lines, load_config(sets=ON))
    assert (resp["date"], resp["time"]) == ("2026-10-01", "18:26")


def test_other_lookalikes():
    assert fix_date_lookalikes("2026.O3.l5") == "2026.03.15"
    assert fix_date_lookalikes("26/1O/D9") == "26/10/09"
    assert fix_date_lookalikes("2026년 1I월 2O일") == "2026년 11월 20일"


def test_existing_date_never_changed():
    lines = L("가나식당", "거래일시 2026-10-20 12:30", "유효 26-1C-01")
    a = extract(lines, load_config())[0]
    b = extract(lines, load_config(sets=ON))[0]
    assert a == b and b["date"] == "2026-10-20"


def test_existing_time_kept():
    lines = L("가나식당", "26-1C-01", "주문 19:05")
    resp, _ = extract(lines, load_config(sets=ON))
    assert (resp["date"], resp["time"]) == ("2026-10-01", "19:05")


def test_non_date_text_unchanged():
    for t in ("TEL 02-1234-5678", "010-1C34-5678", "CODE AB12-1C-01", "COOL-OO-CC", "26-OC-01",
              "쿠프 강남점", "Cafe Dolce 12-CO", "사업자 123-45-67890"):
        assert fix_date_lookalikes(t) == t, t


def test_invalid_after_fix_stays_null():
    for t in ("판매일 26-1C-41", "판매일 26-CC-01", "판매일 26-2C-01"):     # 41일·숫자 없는 월·20월
        resp, _ = extract(L("가나식당", t), load_config(sets=ON))
        assert resp["date"] is None, t


def test_fallback_does_not_touch_name_address_status_logic():
    lines = L("가나마트 노원점", "서울시 노원구 동일로 123", "판매일 2026-1O-20 12:30")
    a = extract(lines, load_config())[0]
    b = extract(lines, load_config(sets=ON))[0]
    assert a["date"] is None and b["date"] == "2026-10-20"
    assert {k: a[k] for k in ("placeName", "address", "addressComplete", "time")} == \
           {k: b[k] for k in ("placeName", "address", "addressComplete", "time")}
    assert (a["status"], b["status"]) == ("PARTIAL", "SUCCESS")


def test_regression_fixtures_unchanged_with_fallback():
    fix = json.load(open(os.path.join(os.path.dirname(__file__), "regression_fixtures.json"), encoding="utf-8"))
    for k, g in fix.items():
        lines = [(t, 0.95) for t in g["lines"]]
        assert extract(lines, load_config())[0] == extract(lines, load_config(sets=ON))[0], k


def test_setting_values_checked():
    for bad in ("on", "true", "fix"):
        try:
            load_config(sets=[f"fields.date_lookalike_fix={bad}"])
        except ValueError:
            continue
        raise AssertionError(bad)
    assert load_config(sets=ON)["_changed"] == {"fields.date_lookalike_fix": "fallback"}
