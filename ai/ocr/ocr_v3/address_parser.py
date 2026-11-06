"""영수증 OCR 줄에서 매장 주소를 찾는다. (S15P31A109-94, 응답 규격 v3)

세 단계를 따로 처리한다.
  1) 후보 찾기   find_candidates : 줄마다 시·도 → 시·군·구 → (읍·면) → 도로명 → 건물번호를 단어 단위로 읽는다
  2) 완전성 판정 is_complete     : OCR 원문에서 그 주소의 건물번호까지 읽었는지 (실제 존재하는 주소인지 검증한 것은 아님)
  3) 반환 정규화 normalize       : 응답에 넣을 문자열을 정책(AddressPolicy)에 따라 만든다

규격 v3 (확정): 반환 주소는 시·도 + 시·군·구 + 도로명까지. 도로명 안의 숫자는 보존 (노원로15길, 한빛로146번길)
  - addressComplete = 원문에서 건물번호까지 읽음. 반환 문자열에 건물번호가 없어도 true 가 될 수 있다
  - 지번 주소만 있으면 시·도 + 시·군·구까지만 반환, complete=False (동·번지는 넣지 않고, 도로명을 만들지 않는다)
  - 주소가 없으면 None, complete=False
  - 배달·고객 주소는 반환하지 않는다. 매장 주소 우선, 본사·제조원 주소는 후순위
  - 문자열의 첫 '로'에서 자르지 않는다
  - 날짜·시간·전화번호·층·호수·동(아파트)·도로명 안의 숫자를 건물번호로 보지 않는다
합의되지 않은 세부 정책은 AddressPolicy 에 모아 두었다 (기본값은 [미합의] 표시, 문서 참고).

PaddleOCR에 의존하지 않는다. receipt_parser.parse_receipt 와 실험 코드(ocrlab)가 함께 쓴다.
"""
from __future__ import annotations

import re
from dataclasses import asdict, dataclass, field
from typing import Optional

# ---------- 정책 ----------

SIDO_FULL = {
    "서울": "서울특별시", "서울시": "서울특별시", "부산": "부산광역시", "부산시": "부산광역시",
    "대구": "대구광역시", "대구시": "대구광역시", "인천": "인천광역시", "인천시": "인천광역시",
    "광주": "광주광역시", "광주시": "광주광역시", "대전": "대전광역시", "대전시": "대전광역시",
    "울산": "울산광역시", "울산시": "울산광역시", "세종": "세종특별자치시", "세종시": "세종특별자치시",
    "경기": "경기도", "강원": "강원특별자치도", "강원도": "강원특별자치도", "충북": "충청북도", "충남": "충청남도",
    "전북": "전북특별자치도", "전라북도": "전북특별자치도", "전남": "전라남도", "경북": "경상북도", "경남": "경상남도",
    "제주": "제주특별자치도", "제주도": "제주특별자치도",
}


@dataclass(frozen=True)
class AddressPolicy:
    """반환 주소 정규화 정책. 확정 항목은 바꾸지 말 것."""
    return_level: str = "road"            # [확정 v3] "road" = 도로명까지. ("building" = 11/02 v2 방식, 비교 실험용)
    sido_style: str = "as_printed"        # [미합의] "as_printed" 영수증 표기 그대로 / "full" 정식 명칭(서울특별시)으로 통일
    include_eupmyeon: bool = True         # [미합의] 도로명 주소의 읍·면을 반환에 포함 (예: 경주시 건천읍 OO로)
    complete_requires_sido: bool = True   # [미합의] 시·도가 인쇄되지 않은 주소('노원구 동일로 12')는 complete=False
    allow_no_sido: bool = True            # [미합의] 시·도 없이 '시·군·구 + 도로명'으로 시작하는 줄도 후보로 인정 (시·도는 채우지 않음)
    join_next_line: bool = True           # [미합의] 주소가 다음 줄로 넘어간 경우 한 줄만 이어 붙임

    def __post_init__(self):
        if self.return_level not in ("road", "building"):
            raise ValueError(f"return_level 은 road 또는 building: {self.return_level}")
        if self.sido_style not in ("as_printed", "full"):
            raise ValueError(f"sido_style 은 as_printed 또는 full: {self.sido_style}")


DEFAULT_POLICY = AddressPolicy()

# ---------- 패턴 ----------

# 시·도: 긴 표기를 먼저 둔다. 앞에 한글이 붙어 있으면(예: '가나서울식당') 시·도로 보지 않는다
_SIDO = ("서울특별시|서울시|서울|부산광역시|부산시|부산|대구광역시|대구시|대구|인천광역시|인천시|인천|"
         "광주광역시|광주시|광주|대전광역시|대전시|대전|울산광역시|울산시|울산|세종특별자치시|세종시|세종|"
         "경기도|경기|강원특별자치도|강원도|강원|충청북도|충북|충청남도|충남|전북특별자치도|전라북도|전북|"
         "전라남도|전남|경상북도|경북|경상남도|경남|제주특별자치도|제주도|제주")
SIDO_RE = re.compile(r"(?<![가-힣])(" + _SIDO + r")")
# '주 소:서울…'에서 OCR 이 ':' 를 빠뜨려 '주 소서울…'처럼 라벨과 시·도가 붙은 경우 (11/02 실사진 진단에서 확인)
LABEL_SIDO_RE = re.compile(r"(?:주\s*소|소\s*재\s*지)\s*[:：]?\s*(" + _SIDO + r")")
SGG_RE = re.compile(r"\s*([가-힣]{1,5}?(?:시|군|구))")              # 시·군·구 (일반구까지 최대 2번)
EUP_RE = re.compile(r"\s*([가-힣]{1,4}?(?:읍|면))")                 # 군 지역 읍·면
# 도로명: 끝이 대로·로·길이고 뒤에 'N길·N번길·N가길'이 붙을 수 있다. 단어 끝에서만 인정
ROAD_RE = re.compile(r"\s*((?=[가-힣0-9.]*[가-힣])[가-힣0-9.]{1,15}?(?:대로|로|길)(?:\d{1,4}(?:번길|가길|길))?)"
                     r"(?=\s|\d|$|[,(\[)]|지하)")
# 건물번호: 123 / 123-4 / 지하 2.
#  - 뒤에 층·호·동·번지·길이 붙으면 건물번호가 아님 (지하1층, 101동, 12번지)
#  - 뒤에 '-숫자'가 더 있거나 ':', '.', '/' + 숫자가 이어지면 날짜·시간·전화번호로 본다
BLDG_RE = re.compile(r"(\s*)((?:지하\s*)?\d{1,5}(?:-\d{1,4})?)(?!\d)(?!층|호|동|번지|번길|가길|길|[-:./]\d)")
_ANOTHER_NUM = re.compile(r"\s+\d{1,5}(?:-\d{1,5})?(?=\s|$|,|\()")  # '노원로15 10' 처럼 숫자가 하나 더 있으면 애매
_DATE_LIKE = re.compile(r"^\s*\d{2,4}[.\-/]\d{1,2}[.\-/]\d{1,2}")
_PHONE_LIKE = re.compile(r"^\s*(?:\d{2,4}-\d{3,4}-\d{4}|\d{4}-\d{4})(?!\d)")
_TIME_LIKE = re.compile(r"^\s*\d{1,2}:\d{2}")
_SGG_START = re.compile(r"(?<![가-힣])([가-힣]{1,4}(?:시|군|구))\s+(?=[가-힣0-9.]{1,15}?(?:대로|로|길))")

CUSTOMER_WORDS = ("배달", "배송", "고객주소", "받는", "수령", "도착지")          # 같은 줄 주소 앞에 있으면 고객 주소
CUSTOMER_LABELS = ("배달주소", "배달지", "배송지", "배송주소", "고객주소", "받는분", "받는 분", "수령지", "수령인", "도착지")
HQ_WORDS = ("본사", "본점", "사무소", "고객센터", "A/S", "AS센터", "제조원", "판매원")  # 후순위
STORE_WORDS = ("주소", "소재지", "매장", "가맹점", "사업장", "점포")             # 우선


# ---------- 1) 후보 ----------

@dataclass
class AddressCandidate:
    line_index: int
    joined_next_line: bool
    sido: Optional[str]
    sgg: list = field(default_factory=list)
    eupmyeon: Optional[str] = None
    road: Optional[str] = None
    building: Optional[str] = None
    role: str = "unknown"          # store / hq / unknown / customer
    excluded: Optional[str] = None  # 제외 이유 (customer 등)
    reason: str = ""

    @property
    def kind(self) -> str:
        """road = 도로명 주소, admin_only = 도로명 없이 시·도·시·군·구까지만 (지번 주소 포함)"""
        return "road" if self.road else "admin_only"

    def to_dict(self):
        d = asdict(self)
        d["kind"] = self.kind
        return d


def _boundary(text: str, end: int) -> bool:
    """시·군·구 바로 뒤가 단어 경계이거나, 붙어 쓴 도로명·읍면이 이어지는지."""
    if end >= len(text) or text[end] in " ,()[]:/":
        return True
    return bool(ROAD_RE.match(text, end) or EUP_RE.match(text, end))


def _read(text: str, pos: int, sido: Optional[str], line_index: int) -> AddressCandidate:
    """pos부터 시·군·구 → 읍·면 → 도로명 → 건물번호를 차례로 읽는다."""
    c = AddressCandidate(line_index=line_index, joined_next_line=False, sido=sido)
    while len(c.sgg) < 2:
        m = SGG_RE.match(text, pos)
        if not m or not _boundary(text, m.end()):
            break
        c.sgg.append(m.group(1))
        pos = m.end()
    m = EUP_RE.match(text, pos)
    if m and _boundary(text, m.end()):
        c.eupmyeon = m.group(1)
        pos = m.end()
    if sido is not None and not c.sgg and not sido.startswith("세종"):
        return c                                      # 시·도 다음에 시·군·구가 없으면 더 읽지 않음
    m = ROAD_RE.match(text, pos)
    if not m:
        return c
    c.road = m.group(1)
    pos = m.end()
    b = BLDG_RE.match(text, pos)
    if not b:
        return c
    if b.group(1) == "" and _ANOTHER_NUM.match(text, b.end()):   # '노원로15 10': 15가 길 이름인지 건물번호인지 모름
        return c
    c.building = re.sub(r"지하\s*", "지하 ", b.group(2))
    return c


def _line_candidate(text: str, line_index: int, policy: AddressPolicy) -> Optional[tuple[int, AddressCandidate]]:
    """한 줄에서 주소 후보 하나를 읽는다. 반환: (줄 안 시작 위치, 후보) 또는 None"""
    hits = {(m.start(1), m.end(1), m.group(1)) for r in (SIDO_RE, LABEL_SIDO_RE) for m in r.finditer(text)}
    for start, end, sido in sorted(hits):              # '제주흑돼지 서울시 …'처럼 앞에 다른 지역명이 있어도 계속 찾는다
        c = _read(text, end, sido, line_index)
        if not c.sgg and not c.road:                   # 시·도만 있는 경우 ('서울특별시 지정 …', '서울우유')는 주소 아님
            continue
        return start, c
    if policy.allow_no_sido:
        m = _SGG_START.search(text)                    # 시·도가 인쇄되지 않은 주소 ('노원구 동일로 12')
        if m:
            c = _read(text, m.start(), None, line_index)
            if c.road:
                return m.start(), c
    return None


def _role(prefix: str) -> str:
    if any(w in prefix for w in HQ_WORDS):
        return "hq"
    if any(w in prefix for w in STORE_WORDS):
        return "store"
    return "unknown"


def _joinable(nxt: str, policy: AddressPolicy) -> bool:
    """다음 줄을 이어 붙여도 되는지: 날짜·전화·시간 줄이나 다른 주소 줄이면 안 된다."""
    return bool(nxt) and not (_DATE_LIKE.match(nxt) or _PHONE_LIKE.match(nxt) or _TIME_LIKE.match(nxt)
                              or _line_candidate(nxt, -1, policy) is not None)


def _richness(c: AddressCandidate) -> int:
    return len(c.sgg) + bool(c.eupmyeon) + bool(c.road) + bool(c.building)


def find_candidates(lines, policy: AddressPolicy = DEFAULT_POLICY) -> list[AddressCandidate]:
    """lines: [(text, score), ...] 또는 [text, ...]. 고객 주소도 excluded 표시와 함께 돌려준다."""
    texts = [((t[0] if isinstance(t, (tuple, list)) else t) or "").strip() for t in lines]
    out: list[AddressCandidate] = []
    customer_ctx = False
    for i, t in enumerate(texts):
        hit = _line_candidate(t, i, policy)
        if hit is None:
            # '배달주소:' 라벨만 있는 짧은 줄 다음 줄의 주소도 고객 주소로 본다
            customer_ctx = len(t) <= 15 and any(w in t.replace(" ", "") for w in CUSTOMER_LABELS)
            continue
        start, c = hit
        prefix = t[:start].replace(" ", "")           # '주 소', '배 달 주 소'처럼 띄어 쓴 라벨도 같게 본다
        if customer_ctx or any(w in prefix for w in CUSTOMER_WORDS):
            c.role, c.excluded = "customer", ("앞줄 배달·고객 라벨" if customer_ctx else "같은 줄 배달·고객 단어")
            customer_ctx = False
            out.append(c)
            continue
        customer_ctx = False
        c.role = _role(prefix)
        # 다음 줄로 넘어간 주소: 도로명이나 건물번호가 다음 줄에 있는 경우 한 줄만 이어 붙인다
        if policy.join_next_line and not is_complete(c, policy) and i + 1 < len(texts) and _joinable(texts[i + 1], policy):
            j = _line_candidate(t + " " + texts[i + 1], i, policy)
            if j and j[0] == start and _richness(j[1]) > _richness(c):
                j[1].role, j[1].joined_next_line = c.role, True
                c = j[1]
        c.reason = {"store": "매장 주소 라벨", "hq": "본사·제조원 주소(후순위)", "unknown": "라벨 없음"}[c.role]
        out.append(c)
    return out


# ---------- 2) 완전성 ----------

def is_complete(c: Optional[AddressCandidate], policy: AddressPolicy = DEFAULT_POLICY) -> bool:
    """원문에서 행정구역 + 도로명 + 건물번호를 모두 읽었는지. 주소가 실제로 존재하는지 검증한 것은 아니다."""
    if c is None or not c.road or not c.building:
        return False
    if c.sido is None:
        return not policy.complete_requires_sido and bool(c.sgg)
    return bool(c.sgg) or c.sido.startswith("세종")


# ---------- 3) 반환 정규화 ----------

def normalize(c: Optional[AddressCandidate], policy: AddressPolicy = DEFAULT_POLICY) -> Optional[str]:
    if c is None:
        return None
    parts = []
    if c.sido:
        parts.append(SIDO_FULL.get(c.sido, c.sido) if policy.sido_style == "full" else c.sido)
    parts += c.sgg
    if c.road:                                   # 도로명 주소
        if c.eupmyeon and policy.include_eupmyeon:
            parts.append(c.eupmyeon)
        parts.append(c.road)                     # 도로명 안의 숫자(15길, 146번길)는 그대로
        if policy.return_level == "building" and c.building:
            parts.append(c.building)
    # 도로명이 없으면(지번 주소 등) 시·도 + 시·군·구까지만. 읍·면·동·번지는 넣지 않는다
    s = " ".join(p for p in parts if p)
    return re.sub(r"\s+", " ", s).strip() or None


# ---------- 선택 ----------

_ROLE_RANK = {"store": 0, "unknown": 1, "hq": 2}


@dataclass
class AddressResult:
    address: Optional[str]
    complete: bool
    selected: Optional[AddressCandidate]
    candidates: list

    def diagnostics(self) -> dict:
        """내부 진단용 (API 응답에 넣지 않는다)"""
        return {"address": self.address, "complete": self.complete,
                "selected_line": None if self.selected is None else self.selected.line_index,
                "candidates": [c.to_dict() for c in self.candidates]}


def extract_address_detail(lines, policy: AddressPolicy = DEFAULT_POLICY) -> AddressResult:
    cands = find_candidates(lines, policy)
    usable = [c for c in cands if not c.excluded]
    best = min(usable, key=lambda c: (_ROLE_RANK[c.role], c.line_index)) if usable else None
    return AddressResult(normalize(best, policy), is_complete(best, policy), best, cands)


def extract_address(lines, policy: AddressPolicy = DEFAULT_POLICY) -> tuple[Optional[str], bool]:
    """반환: (address | None, addressComplete). receipt_parser.parse_receipt 가 쓰는 함수."""
    r = extract_address_detail(lines, policy)
    return r.address, r.complete
