"""상호명 선택 v2 (fields.name_rule). 가상 영수증 값만 쓴다 (실제 상호·대표자 아님).
- 기본값(legacy)은 운영과 같아야 한다
- v2 는 찾았을 때만 바꾼다. 못 찾으면 기존 값을 그대로 둔다
- 11/05 dev2 진단에서 나온 실패 유형을 막는다"""
import json
import os

from ocrlab.fields import extract, name_v2
from ocrlab.settings import load_config

V2 = ["fields.name_rule=v2"]


def L(*t):
    return [(x, 0.95) for x in t]


def test_default_legacy_same_as_parse_receipt():
    from receipt_parser import parse_receipt
    cfg = load_config()
    assert cfg["fields"]["name_rule"] == "legacy" and cfg["_changed"] == {}
    lines = L("가나식당 TID: ***1234567", "홍길동 123-90-12345", "서울 노원구 동일로180길 32")
    assert extract(lines, cfg)[0]["placeName"] == parse_receipt(lines).placeName


def test_cut_tid_and_bracket_label():
    assert name_v2(L("신용매출 18:57:11", "가나축산물센타 TID: ***1234567", "홍길동 123-90-12345 Tel:01012345678"))[0] == "가나축산물센타"
    assert name_v2(L("[매장명] 다라커피(노원역점) [사업자] 123-16-12345", "[주 소] 서울 노원구 상계로1길 14"))[0] == "다라커피(노원역점)"


def test_label_must_start_line():
    v, why = name_v2(L("라라올리브영(주) 양주덕정점", "경기도 양주시 회정로 135", "결제수단과 영수증 지참 후 매장 방문시 가능"))
    assert v == "라라올리브영(주) 양주덕정점" and why == "지점명 줄"      # '매장 방문시' 는 라벨 아님, 지점 뒤 (주) 앞에서 자르지 않음


def test_leading_bizno_latin_logo_and_junk():
    assert name_v2(L("LOTTERIA TM", "123-46-12345 마바리아 의정부시청점", "대표 홍길동"))[0] == "마바리아 의정부시청점"
    assert name_v2(L("emarteveryday", "emarlt 사사마트에브리데이 덕정점 123-86-12345", "everyday 경기 양주시"))[0] == "사사마트에브리데이 덕정점"
    assert name_v2(L("(주)코리아세븐 7-ELEVEN www.7-eleven.co.kr", "(--) 아자일레븐 잠실광장점#19290"))[0] == "아자일레븐 잠실광장점#19290"


def test_logo_and_corp_in_middle_of_branch_line():
    v, _ = name_v2(L("아자일레븐 (주)코리아세븐 7-ELEVEN 고암중앙점#59501 www.7-eleven.co.kr", "(--)"))
    assert v == "아자일레븐 고암중앙점#59501"


def test_corp_first_and_slash_kept():
    assert name_v2(L("(주)럭키엠케이유(럭키할인마트", "역삼점)", "서울 강남구 역삼로 120"))[0] == "(주)럭키엠케이유(럭키할인마트 역삼점)"
    assert name_v2(L("[ 영 수 증]", "차카초밥/220-05-12345 / 홍길동", "2026-10-30 18:23:57"))[0] == "차카초밥"


def test_stop_at_item_header_and_keep_legacy_when_none():
    lines = L("[ 영 수 증]", "서울 종로구 종로 200-4 1층", "상 품 명 단 가 수량 금 액", "상차림 0 2 0")
    assert name_v2(lines) == (None, "후보 없음")
    assert extract(lines, load_config(sets=V2))[0]["placeName"] == extract(lines, load_config())[0]["placeName"]


def test_address_label_value_rejected():
    lines = L("가마치킨 역삼점", "본사: 서울시 강남구 한빛로 7", "매장: 서울시 노원구 동일로 12", "2026-10-22 12:03")
    assert extract(lines, load_config(sets=V2))[0]["placeName"] == "가마치킨 역삼점"


def test_regression_fixtures_v2():
    """기존 회귀 정답과 같아야 한다. hq_store 하나만 의도된 개선 (주소를 상호로 고르던 것 → 지점명)."""
    fix = json.load(open(os.path.join(os.path.dirname(__file__), "regression_fixtures.json"), encoding="utf-8"))
    for k, g in fix.items():
        lines = [(t, 0.95) for t in g["lines"]]
        a = extract(lines, load_config())[0]
        b = extract(lines, load_config(sets=V2))[0]
        if k == "hq_store":
            assert b["placeName"] == "가마치킨 역삼점", b
            continue
        assert a == b, (k, a, b)


def test_setting_values_checked():
    try:
        load_config(sets=["fields.name_rule=v9"])          # 11/09 v3 가 생겨 없는 값 예시를 v9 로 바꿈
    except ValueError:
        pass
    else:
        raise AssertionError("잘못된 값을 받음")
    assert load_config(sets=V2)["_changed"] == {"fields.name_rule": "v2"}


# ---------- 2차 (11/05, v2 다시 채점에서 나빠진 6장 유형) ----------

def test_label_value_joins_next_line():
    lines = L("가나 사업자번모:123-75-12345", "상품명 단가 수량 금액", "상호: (주)가나엠케이(가나할인마트", "역삼점)")
    assert name_v2(lines)[0] == "(주)가나엠케이(가나할인마트 역삼점)"


def test_open_paren_corp_fixed_like_legacy():
    assert name_v2(L("[진동벨 [번호 :66]", "(주가나빈코리아 트레이드타워점", "사업자 번호 : 123-86-12345"))[0] == "(주)가나빈코리아 트레이드타워점"


def test_tail_after_branch_cut():
    assert name_v2(L("아자일레븐 역삼태광점#69042 (주)코리아세븐 T-ELEVEN www.7-eleven.co.kr", "(--)"))[0] == "아자일레븐 역삼태광점#69042"


def test_bracket_label_in_middle_of_line_and_one_char_branch():
    lines = L("[영수증]", "[주 소] 서울 구로구 공원로 83 (구로동). [매장명]카페가나/123-22-12345", "내점 주문번회 0047", "상품명 단가 수량 금액")
    assert name_v2(lines) == ("카페가나", "라벨 뒤 값")
    assert name_v2(L("내점 주문번호 0047", "가나분식"))[0] == "가나분식"          # '내점'·주문번호 줄은 상호 아님


def test_name_after_address_with_slash_bizno_and_fragment():
    lines = L("[영 수 증]", "서울 강남구 테헤란로20길 19(역삼동,엘지 가나초밥/123-05-12345/홍길동", "에들라트)1층")
    assert name_v2(lines)[0] == "가나초밥"
    assert name_v2(L("역삼점)", "가나마트"))[0] == "가나마트"                      # 닫는 괄호만 있는 조각은 후보 아님
