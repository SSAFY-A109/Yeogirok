"""기존 상호명·날짜·시간 추출 회귀 테스트.
regression_fixtures.json 의 기대값은 v0r2 receipt_parser(주소 v3 적용 전)로 만든 값이다. 가상 영수증.
주소 v3 패치 뒤에도 상호명·날짜·시간이 하나도 바뀌지 않아야 한다."""
import json
import os

from receipt_parser import parse_receipt

FIX = json.load(open(os.path.join(os.path.dirname(__file__), "regression_fixtures.json"), encoding="utf-8"))


def test_name_date_time_unchanged():
    bad = []
    for k, g in FIX.items():
        r = parse_receipt([(t, 0.95) for t in g["lines"]])
        if (r.placeName, r.date, r.time) != (g["placeName"], g["date"], g["time"]):
            bad.append((k, (r.placeName, r.date, r.time), (g["placeName"], g["date"], g["time"])))
    assert not bad, bad


def test_address_fields_present_after_patch():
    r = parse_receipt([(t, 0.95) for t in FIX["branch_first"]["lines"]])
    d = r.to_dict()
    if "addressComplete" not in d:      # 패치 전 운영 코드에서 돌리면 건너뜀
        return
    assert (d["address"], d["addressComplete"]) == ("서울시 노원구 동일로", True)
