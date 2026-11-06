"""주소 v3 테스트: 반환은 도로명까지, addressComplete 는 원문의 건물번호 인식 여부. 모든 주소·상호는 가상 값."""
from address_parser import (AddressPolicy, extract_address, extract_address_detail, find_candidates,
                            is_complete, normalize)

D = "2026-10-20 12:30"


def L(*texts, score=0.95):
    return [(t, score) for t in texts]


def A(*texts, policy=None):
    return extract_address(L(*texts)) if policy is None else extract_address(L(*texts), policy)


# ---------- 필수 사례 ----------

def test_road_and_building_complete():                 # 도로명 + 건물번호 → true, 반환은 도로명까지
    assert A("가나식당", "서울시 노원구 동일로 123", D) == ("서울시 노원구 동일로", True)


def test_road_only_incomplete():                       # 도로명까지만 → false
    assert A("가나식당", "서울시 노원구 동일로", D) == ("서울시 노원구 동일로", False)


def test_numbered_road_only_incomplete():              # 숫자가 들어간 도로명만 → false, 숫자 보존
    assert A("가나식당", "서울특별시 노원구 노원로15길", D) == ("서울특별시 노원구 노원로15길", False)


def test_numbered_road_with_building():
    assert A("가나식당", "서울시 노원구 노원로15길 10", D) == ("서울시 노원구 노원로15길", True)
    assert A("가나식당", "경기도 수원시 팔달구 한빛로146번길 20", D) == ("경기도 수원시 팔달구 한빛로146번길", True)


def test_jibun_only():                                 # 지번만 → 시·도 + 시·군·구, false
    assert A("가나식당", "서울시 노원구 공릉동 123-4", D) == ("서울시 노원구", False)


def test_no_address():
    assert A("가나식당", D, "합계 10,000") == (None, False)
    assert A() == (None, False)


def test_candidates_not_mixed():                       # 서로 다른 주소의 도로명과 건물번호를 합치지 않는다
    assert A("가나식당", "본사: 서울시 강남구 한빛로", "매장: 서울시 노원구 동일로 12") == ("서울시 노원구 동일로", True)
    assert A("가나식당", "서울시 강남구 한빛로", "서울시 노원구 동일로 12") == ("서울시 강남구 한빛로", False)
    # 다음 줄이 다른 주소면 이어 붙이지 않는다
    r = extract_address_detail(L("서울시 강남구 한빛로", "노원구 동일로 12"))
    assert r.selected.building is None and not r.selected.joined_next_line


def test_building_on_next_line():
    assert A("서울시 노원구 동일로", "123, 2층", D) == ("서울시 노원구 동일로", True)


# ---------- 건물번호로 오판하지 않기 ----------

def test_not_building_date_time_phone():
    assert A("서울시 노원구 동일로 2026-10-20 12:30") == ("서울시 노원구 동일로", False)
    assert A("서울시 노원구 동일로 12:30") == ("서울시 노원구 동일로", False)
    assert A("서울시 노원구 동일로 02-123-4567") == ("서울시 노원구 동일로", False)
    assert A("서울시 노원구 동일로", "1588-1234") == ("서울시 노원구 동일로", False)
    assert A("서울시 노원구 동일로", "02-123-4567") == ("서울시 노원구 동일로", False)


def test_not_building_floor_unit():
    assert A("서울시 노원구 동일로 1층") == ("서울시 노원구 동일로", False)
    assert A("서울시 노원구 동일로 101동") == ("서울시 노원구 동일로", False)
    assert A("서울시 노원구 동일로 지하1층") == ("서울시 노원구 동일로", False)
    assert A("서울시 노원구 동일로 12 지하1층 B102호") == ("서울시 노원구 동일로", True)


def test_road_number_is_not_building():
    assert A("서울시 노원구 노원로15길") == ("서울시 노원구 노원로15길", False)      # 15는 도로명 일부
    assert A("서울시 노원구 노원로15 10") == ("서울시 노원구 노원로", False)          # 15가 길 번호인지 건물번호인지 모름


def test_basement_building_and_glued():
    assert A("서울시 중구 세종대로 지하 2") == ("서울시 중구 세종대로", True)
    assert A("서울시 노원구 동일로123") == ("서울시 노원구 동일로", True)


def test_customer_address_not_returned():
    assert A("가나식당", "배달주소: 서울시 노원구 동일로 12") == (None, False)
    assert A("가나식당", "주소: 서울시 강남구 한빛로 7", "배달주소: 서울시 노원구 동일로 12") == ("서울시 강남구 한빛로", True)
    r = extract_address_detail(L("배달주소: 서울시 노원구 동일로 12"))
    assert r.candidates[0].excluded and r.selected is None


# ---------- 단계 분리 ----------

def test_stages_separate():
    c = find_candidates(L("주소: 서울특별시 노원구 노원로15길 10, 2층"))[0]
    assert (c.road, c.building, c.role) == ("노원로15길", "10", "store")
    assert is_complete(c) is True
    assert normalize(c) == "서울특별시 노원구 노원로15길"
    assert normalize(c, AddressPolicy(return_level="building")) == "서울특별시 노원구 노원로15길 10"


def test_policy_options():
    full = AddressPolicy(sido_style="full")
    assert A("서울시 노원구 동일로 1", policy=full) == ("서울특별시 노원구 동일로", True)
    assert A("경북 경주시 건천읍 한빛로 5") == ("경북 경주시 건천읍 한빛로", True)
    assert A("경북 경주시 건천읍 한빛로 5", policy=AddressPolicy(include_eupmyeon=False)) == ("경북 경주시 한빛로", True)
    # 시·도가 인쇄되지 않은 주소: 기본은 complete False (시·도를 채우지 않음)
    assert A("노원구 동일로 12") == ("노원구 동일로", False)
    assert A("노원구 동일로 12", policy=AddressPolicy(complete_requires_sido=False)) == ("노원구 동일로", True)
    assert A("노원구 동일로 12", policy=AddressPolicy(allow_no_sido=False)) == (None, False)


def test_bad_policy_value():
    try:
        AddressPolicy(return_level="dong")
    except ValueError:
        return
    raise AssertionError("잘못된 정책 값이 통과함")


def test_diagnostics_has_no_side_effect():
    r = extract_address_detail(L("가나식당", "서울시 노원구 동일로 123"))
    d = r.diagnostics()
    assert d["selected_line"] == 1 and d["candidates"][0]["kind"] == "road"


# ---------- 11/02 실사진 진단에서 확인한 경우 (가상 값으로 재현) ----------

def test_label_glued_sido():
    # OCR 이 '주 소:' 의 ':' 를 빠뜨려 '주 소서울…' 이 된 경우: 시·도를 인정한다
    assert A("주 소서울 노원구 동일로 120 1층") == ("서울 노원구 동일로", True)
    assert A("가나 대 표 홍 주 소서울 노원구 동일로 120 1층 자:홍 노원점)") == ("서울 노원구 동일로", True)
    assert A("주소서울시 노원구 동일로 12") == ("서울시 노원구 동일로", True)
    assert A("소재지서울특별시 노원구 동일로 12") == ("서울특별시 노원구 동일로", True)
    # 라벨이 아닌 한글 뒤에 붙은 지역명은 여전히 시·도로 보지 않는다
    assert A("가나서울식당 노원구 동일로 12") == ("노원구 동일로", False)


def test_spaced_customer_label():
    assert A("배 달 주 소 : 서울시 노원구 동일로 12") == (None, False)
