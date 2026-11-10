"""상호명 v3 (fields.name_rule=v3)·도로명 혼동 문자 보정 (fields.addr_lookalike_fix=on). 가상 영수증 값만 쓴다 (실제 상호·대표자 아님).
- 기본값(legacy)·v2 는 바뀌지 않는다
- v3 는 찾았을 때만 바꾼다
- 11/09 dev3 진단에서 나온 실패 유형을 막는다"""
from ocrlab.fields import extract, fix_road_lookalikes, name_v2, name_v3
from ocrlab.settings import load_config


def L(*t):
    return [(x, 0.95) for x in t]


CARD = L("키스-체크 가맹점명/주소가 실제와 다른경우", "KIS정보통신 신고안내(포상금 10만원 지급) 여신금융협회(02-0000-0000)",
         "가나마스터 승인", "할부: [일시불]", "거래일시: 26/01/02 10:11:12", "금 액: 1,455원", "합 계: 1,600원",
         "승인번호(가나카드) 00001234", "가맹점/사업자번호 123456789/123-45-67890")


def test_defaults_unchanged():
    cfg = load_config()
    assert cfg["fields"]["name_rule"] == "legacy" and cfg["fields"]["addr_lookalike_fix"] == "legacy"


def test_card_slip_owner_tel_then_name():
    v, why = name_v3(CARD + L("홍길동(TEL:0212345678) 가나의원", "서울특별시 강남구 가나대로 12 3층"))
    assert (v, why) == ("가나의원", "카드전표 가맹점 줄")
    v, why = name_v3(CARD + L("홍길동(TEL:0212345678)", "가나다찹쌀순대(라마점)", "서울 강남구 가나대로12길 6 1층"))
    assert (v, why) == ("가나다찹쌀순대(라마점)", "카드전표 가맹점 줄")
    assert name_v2(CARD + L("홍길동(TEL:0212345678) 가나의원"))[0] != "가나의원"       # v2 는 그대로


def test_card_slip_stops_at_address():
    v, _ = name_v3(CARD + L("홍길동(TEL:0212345678)", "서울특별시 강남구 가나대로 12 3층", "가나의원"))
    assert v != "가나의원"


def test_bizno_then_name_merged_line():
    lines = L("카드판매 [ 영수증 ]", "[고객용]", "사업자번호 : 123-45-67890 가나다라마바101",
              "서울특별시 강남구 가나로93길 20 지하1층 대표자: 홍길동 TEL:", "테이블명: 1", "상품 단가 수량 금액")
    assert name_v3(lines)[0] == "가나다라마바101"


def test_slash_bizno_short_digit():
    lines = L("[ 영 수증]", "가나스토리 / 123-4-56789 / 홍길동 서울 강남구 가나로85길 25 (가나동) 1층", "상품 명 단가 수량 금 액")
    assert name_v3(lines)[0] == "가나스토리"


def test_merged_two_column_labels():
    lines = L("가맹점명 사업자NO 가나청년약국 1234567890 대표자명 : 홍길동", "주 전화번호 소 : 070-0000-0000 서울 강남구 가나대로 1",
              "카드(일시불)", "상품명/제약사 단가 수량 금액")
    assert name_v3(lines)[0] == "가나청년약국"


def test_notice_and_lot_number_not_name():
    assert name_v3(L("KIS정보통신 신고안내(포상금 10만원 지급)", "할부: [일시불]", "상품명 수량"))[0] is None
    v, _ = name_v3(L("[주 [사업자] [매장명] 소] 가나시 다라길 30-17 123-45-67890", "상품 명 단 가 수량 금 액"))
    assert v is None or "30-17" not in v


def test_v3_in_extract_only_when_set():
    lines = CARD + L("홍길동(TEL:0212345678) 가나의원", "서울특별시 강남구 가나대로 12 3층")
    assert extract(lines, load_config(sets=["fields.name_rule=v3"]))[0]["placeName"] == "가나의원"
    assert extract(lines, load_config())[0]["placeName"] != "가나의원"


def test_road_gil_lookalike():
    assert fix_road_lookalikes("서울 강남구 가나로85일 25 (가나동) 1층") == "서울 강남구 가나로85길 25 (가나동) 1층"
    assert fix_road_lookalikes("2026년 11월 5일 10:00") == "2026년 11월 5일 10:00"          # 날짜는 그대로
    assert fix_road_lookalikes("서울 강남구 가나로 25") == "서울 강남구 가나로 25"
    lines = L("가나스토리 / 123-45-67890 / 홍길동", "서울 강남구 논현로85일 25 (가나동) 1층")
    on = extract(lines, load_config(sets=["fields.addr_lookalike_fix=on"]))[0]
    off = extract(lines, load_config())[0]
    assert on["address"] == "서울 강남구 논현로85길" and on["addressComplete"] is True
    assert off["address"] != on["address"]


def test_branch_then_brand_word_kept():
    lines = L("[영수증]", "교환권 번호 : 186", "가나동라마공원점 바사아익스프레스", "서울특별시 가나구 다라로2길 23-3, 1층", "상품명")
    assert name_v3(lines)[0] == "가나동라마공원점 바사아익스프레스"
    assert name_v2(lines)[0] == "가나동라마공원점"                      # v2 는 그대로 (지점명까지만)
    assert name_v3(L("가나일레븐 다라점#12345 T-ELEVEN", "상품명"))[0] == "가나일레븐 다라점#12345"   # 영문 꼬리는 그대로 자름


def test_card_slip_name_on_head_line():
    # 줄 묶기 v2 에서 상호가 '가맹점/사업자번호' 줄 오른쪽에 붙는 경우
    lines = CARD[:-1] + L("가맹점/사업자번호 123456789/123-45-67890 가나의원", "홍길동(TEL:0212345678)", "서울특별시 강남구 가나대로 12 3층")
    assert name_v3(lines) == ("가나의원", "카드전표 가맹점 줄")
    assert name_v3(CARD + L("홍길동(TEL:0212345678)", "가나다찹쌀순대(라마점)", "서울 강남구 가나대로12길 6 1층"))[0] == "가나다찹쌀순대(라마점)"
