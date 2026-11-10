"""줄 묶기 v2 (fields.line_group=v2, ocrlab/lines.py). 가상 박스 좌표·글자만 쓴다.
- 기본(legacy)은 receipt_parser.group_lines 와 같아야 한다
- 반듯한 영수증은 v2 도 같은 줄이 나와야 한다
- 11/09 dev3 진단 유형: 기운 두 칸 라벨, 키 큰 박스, 위아래로 쌓인 글자 박스는 줄이 합쳐지지 않아야 한다"""
import math

from ocrlab.lines import group_lines_v2, make_lines
from ocrlab.settings import load_config


def box(cx, cy, w, h, deg=0.0):
    a = math.radians(deg)
    ux, uy, vx, vy = math.cos(a), math.sin(a), -math.sin(a), math.cos(a)
    pts = []
    for sx, sy in ((-1, -1), (1, -1), (1, 1), (-1, 1)):       # 왼위·오른위·오른아래·왼아래
        pts.append([cx + sx * w / 2 * ux + sy * h / 2 * vx, cy + sx * w / 2 * uy + sy * h / 2 * vy])
    return pts


def run(items, cfg=None):
    t = [i[0] for i in items]
    return make_lines(t, [0.95] * len(t), [box(*i[1:]) for i in items], cfg)


V2 = load_config(sets=["fields.line_group=v2"])
STRAIGHT = [("거래일시:", 120, 100, 160, 30), ("2026-01-02 10:11:12", 420, 101, 300, 30),
            ("합 계:", 100, 150, 120, 30), ("1,600원", 450, 149, 140, 30),
            ("가나식당", 300, 200, 200, 32), ("서울 강남구 가나로 12", 300, 245, 380, 30)]


def test_default_is_receipt_parser():
    from receipt_parser import boxes_from_polys, group_lines
    t = [i[0] for i in STRAIGHT]
    p = [box(*i[1:]) for i in STRAIGHT]
    assert make_lines(t, [0.95] * len(t), p, load_config()) == group_lines(boxes_from_polys(t, [0.95] * len(t), p))


def test_straight_receipt_same_as_legacy():
    assert [x for x, _ in run(STRAIGHT, V2)] == [x for x, _ in run(STRAIGHT)]
    assert [x for x, _ in run(STRAIGHT, V2)] == ["거래일시: 2026-01-02 10:11:12", "합 계: 1,600원", "가나식당", "서울 강남구 가나로 12"]


def test_tilted_two_column_labels_not_merged():
    # 10도 기운 영수증, 줄 간격 25px, 왼쪽 라벨·오른쪽 값 (기존 방식은 기운 박스 높이가 커져 세 줄이 하나로 합쳐짐)
    t = math.tan(math.radians(10))
    items = []
    for i, (lab, val) in enumerate((("[매장명]", "가나다"), ("[사업자]", "123-45-67890"), ("[주 소]", "가나시 다라길 30-17 (마바동) 1층"))):
        y = 300 + i * 25
        items.append((lab, 230, y, 60, 22, 10))
        w = 60 + 12 * len(val)
        cx = 280 + w / 2
        items.append((val, cx, y + t * (cx - 230), w, 22, 10))
    legacy = [x for x, _ in run(items)]
    v2 = [x for x, _ in run(items, V2)]
    assert len(legacy) < 3                                           # 기존 방식은 합쳐짐 (진단과 같은 현상)
    assert v2 == ["[매장명] 가나다", "[사업자] 123-45-67890", "[주 소] 가나시 다라길 30-17 (마바동) 1층"]


def test_tall_box_does_not_pull_neighbors():
    items = [("가나다라마바", 200, 100, 200, 20), ("교환권 번호 : 12", 200, 60, 260, 44),
             ("서울 강남구 가나로85길 3", 220, 125, 300, 20), ("1234567890 / 홍길동", 200, 150, 260, 20)]
    v2 = [x for x, _ in run(items, V2)]
    assert v2 == ["교환권 번호 : 12", "가나다라마바", "서울 강남구 가나로85길 3", "1234567890 / 홍길동"]


def test_stacked_char_boxes_are_separate_lines():
    # 글자 하나씩 잡힌 박스: 같은 x 에 위아래로 쌓이면 다른 줄
    items = [("가", 100, 100, 16, 16), ("나", 118, 102, 16, 16), ("다", 136, 103, 16, 16),
             ("라", 100, 112, 16, 10), ("마", 118, 114, 16, 10)]
    v2 = [x for x, _ in run(items, V2)]
    assert v2 == ["가 나 다", "라 마"]


def test_bad_value_rejected():
    try:
        load_config(sets=["fields.line_group=v9"])
    except ValueError:
        return
    raise AssertionError("잘못된 값을 받음")
