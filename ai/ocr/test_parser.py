from receipt_parser import Box, group_lines, parse_receipt


def L(*texts, score=0.95):
    return [(t, score) for t in texts]


def test_labeled_pos_receipt():
    r = parse_receipt(L(
        "[영수증]",
        "상호: 바다향기 횟집 대표: 홍길동",
        "사업자번호 123-45-67890",
        "주소: 강원 강릉시 창해로 307",
        "TEL 033-123-4567",
        "거래일시 2026-10-25 19:42:10",
        "합계 58,000",
    ))
    assert r.placeName == "바다향기 횟집"
    assert r.address == "강원 강릉시 창해로 307"
    assert r.visitedAt == "2026-10-25T19:42:00+09:00"
    assert r.confidence == "high"


def test_unlabeled_cafe_ampm():
    r = parse_receipt(L(
        "카페 온더록",
        "제주특별자치도 제주시 애월읍 애월해안로 272",
        "2026/10/24 오후 03:15",
        "아메리카노 2 9,000",
    ))
    assert r.placeName == "카페 온더록"
    assert r.address.startswith("제주")
    assert (r.date, r.time) == ("2026-10-24", "15:15")


def test_missing_time_is_low():
    r = parse_receipt(L("국립중앙박물관", "26.10.20"))
    assert r.date == "2026-10-20" and r.time is None
    assert r.confidence == "low"


def test_group_lines_merges_same_row():
    lines = group_lines([
        Box("15,000", 0.9, 300, 101, 20),
        Box("합계", 0.9, 10, 100, 20),
        Box("상호", 0.9, 10, 50, 20),
    ])
    assert [t for t, _ in lines] == ["상호", "합계 15,000"]


# ---- S15P31A109-77 상호명 규칙 개선 ----
def test_spaced_title_line_is_not_name():
    r = parse_receipt(L("영 수 증", "바다식당", "2026-10-27 18:42"))
    assert r.placeName == "바다식당"


def test_reissue_title_line_is_not_name():
    r = parse_receipt(L("영 수 증 -재발행", "산들식육식당 / 529-19-02082 / 홍길동", "2026-02-25 21:26:03"))
    assert r.placeName == "산들식육식당"


def test_slash_business_number_is_cut():
    r = parse_receipt(L("[ 영수증 ]", "바다식당/463-56-00299/홍길동", "2022-10-04 19:17:14"))
    assert r.placeName == "바다식당"


def test_spaced_label_is_removed():
    r = parse_receipt(L("점 포명:948키친", "사업자 번호: 176-12-02249", "[등록] 2024-12-13 13:04"))
    assert r.placeName == "948키친"


if __name__ == "__main__":   # pytest 없이도 실행: python test_parser.py
    tests = [f for n, f in sorted(globals().items()) if n.startswith("test_") and callable(f)]
    failed = 0
    for t in tests:
        try:
            t()
            print(f"PASS {t.__name__}")
        except AssertionError as e:
            failed += 1
            print(f"FAIL {t.__name__} {e}")
    print(f"\n{len(tests) - failed}/{len(tests)} 통과")
    raise SystemExit(1 if failed else 0)
