"""[비교용] 11/02 v2 방식(건물번호까지 반환) 정책으로 돌리는 주소 테스트. 후보 찾기·완전성 판정이 v2와 같은지 확인한다.
원본: address_v2/test_address.py (S15P31A109-94). 모든 주소·상호는 가상 값 (요구사항 예시 도로명 일부 포함).
T번호는 API/영수증_주소추출_요구사항_Jira_초안.md 4-1 표와 같다.
"""
from address_parser import AddressPolicy, extract_address

BUILDING = AddressPolicy(return_level="building")

D = "2026-10-20 12:30"


def L(*texts, score=0.95):
    return [(t, score) for t in texts]


def A(*texts):
    return extract_address(L(*texts), BUILDING)


# ---------- 건물번호까지 다 읽음 → complete True ----------

def test_t01_road_basic():
    assert A("가나식당", "서울시 노원구 동일로 123", D) == ("서울시 노원구 동일로 123", True)


def test_t02_road_n_gil():
    assert A("가나식당", "서울특별시 노원구 노원로15길 10", D) == ("서울특별시 노원구 노원로15길 10", True)


def test_t03_daero():
    assert A("가나식당", "서울시 중구 세종대로 110", D) == ("서울시 중구 세종대로 110", True)


def test_t04_beongil_and_general_gu():
    assert A("가나식당", "경기도 수원시 팔달구 한빛로146번길 20", D) == ("경기도 수원시 팔달구 한빛로146번길 20", True)


def test_t05_gil_name_plus_n_gil_drops_ho():
    assert A("가나식당", "서울특별시 성동구 하늬바람길34길 166 226호", D) == ("서울특별시 성동구 하늬바람길34길 166", True)


def test_t06_label_and_basement_floor():
    assert A("가나식당", "주소: 서울특별시 강남구 한빛로7길 103 지하1층", D) == ("서울특별시 강남구 한빛로7길 103", True)


def test_t07_basement_building_number():
    assert A("가나식당", "서울시 중구 한빛대로 지하 2", D) == ("서울시 중구 한빛대로 지하 2", True)


def test_t08_comma_floor_ho_parenthesis():
    assert A("가나식당", "서울시 노원구 동일로 123, 2층 201호 (가나동)", D) == ("서울시 노원구 동일로 123", True)


def test_t09_glued_building_number():
    assert A("가나식당", "서울시 노원구 동일로123", D) == ("서울시 노원구 동일로 123", True)


def test_t10_no_spaces_at_all():
    assert A("가나식당", "서울특별시노원구노원로15길10", D) == ("서울특별시 노원구 노원로15길 10", True)


def test_t11_two_lines():
    assert A("가나식당", "주소: 서울특별시 마포구", "은하수로 116 (아라동)", D) == ("서울특별시 마포구 은하수로 116", True)


def test_t12_gu_name_contains_ro():
    assert A("가나식당", "서울특별시 종로구 한빛로 12", D) == ("서울특별시 종로구 한빛로 12", True)


def test_t13_eup():
    assert A("가나식당", "제주특별자치도 제주시 애월읍 애월해안로 272", D) == ("제주특별자치도 제주시 애월읍 애월해안로 272", True)


def test_t14_sejong_without_sigungu():
    assert A("가나식당", "세종특별자치시 가온로 12", D) == ("세종특별자치시 가온로 12", True)


def test_t16_store_before_hq():
    assert A("가나식당", "주소: 서울시 노원구 동일로 123", "본사: 부산광역시 해운대구 누리로 45", D) == ("서울시 노원구 동일로 123", True)


def test_t17_hq_first_then_store_label():
    assert A("가나식당", "본사: 부산광역시 해운대구 누리로 45", "매장: 서울시 노원구 동일로 123", D) == ("서울시 노원구 동일로 123", True)


def test_t26_address_and_name_on_same_line():
    line = "서울 강남구 테헤란로1길 9(역삼동,가나 바다초밥/123-45-67890/홍길동"
    assert A("[영수증]", line, "2026-10-27 18:42:03") == ("서울 강남구 테헤란로1길 9", True)


def test_t27_sub_number():
    assert A("가나식당", "서울시 노원구 동일로 123-4", D) == ("서울시 노원구 동일로 123-4", True)


def test_t29_building_number_on_next_line():
    assert A("가나식당", "서울특별시 마포구 은하수로", "116 2층", D) == ("서울특별시 마포구 은하수로 116", True)


def test_t30_building_name_after_number():
    assert A("가나식당", "서울시 노원구 동일로 123 가나빌딩 3층", D) == ("서울시 노원구 동일로 123", True)


# ---------- 일부만 읽음 → 읽은 데까지, complete False ----------

def test_t18_jibun_only_returns_admin_part():
    assert A("가나식당", "서울 강남구 가나동 123-4", D) == ("서울 강남구", False)


def test_t20_only_sido_sigungu():
    assert A("가나식당", "서울시 노원구", D) == ("서울시 노원구", False)


def test_t21_misread_road_suffix():
    assert A("가나식당", "서울시 노원구 동일루 123", D) == ("서울시 노원구", False)


def test_t22_ambiguous_number_after_road():
    assert A("가나식당", "서울특별시 노원구 노원로15 10", D) == ("서울특별시 노원구 노원로", False)


def test_t23_no_sido_printed():
    assert A("가나식당", "노원구 동일로 12", D) == ("노원구 동일로 12", False)


def test_t28_no_building_number():
    assert A("가나식당", "서울시 노원구 동일로", D) == ("서울시 노원구 동일로", False)


def test_phone_on_next_line_is_not_building_number():
    assert A("가나식당", "서울시 노원구 동일로", "02-123-4567", D) == ("서울시 노원구 동일로", False)


# ---------- 반환하지 않음 → (None, False) ----------

def test_t15_no_address():
    assert A("가나식당", D) == (None, False)


def test_t19_delivery_address_same_line():
    assert A("가나치킨", "배달주소: 서울시 노원구 동일로 123 101동 202호", D) == (None, False)


def test_delivery_label_on_previous_line():
    assert A("가나치킨", "배달주소:", "서울시 노원구 동일로 123 101동 202호", D) == (None, False)


def test_store_address_kept_when_delivery_address_exists():
    got = A("가나치킨", "주소: 서울시 중구 한빛대로 12", "배달주소: 서울시 노원구 동일로 123 101동", D)
    assert got == ("서울시 중구 한빛대로 12", True)


def test_t24_name_with_ro():
    assert A("커피로스터스 노원점", D) == (None, False)


def test_t25_item_with_region_name():
    assert A("가나식당", "부산어묵 2 3,000", D) == (None, False)


def test_item_starting_with_region_name():
    assert A("가나카페", "제주말차 크림프라푸치노 그란데 사이즈", "4,600 1 4,600") == (None, False)


def test_sido_only_phrase():
    assert A("가나식당", "서울특별시 지정 모범음식점", D) == (None, False)


def test_region_word_inside_name_then_real_address():
    assert A("제주흑돼지 서울시 노원구 동일로 12", D) == ("서울시 노원구 동일로 12", True)


if __name__ == "__main__":
    tests = [v for k, v in dict(globals()).items() if k.startswith("test_")]
    for t in tests:
        t()
        print("PASS", t.__name__)
    print(f"{len(tests)}/{len(tests)} 통과")
