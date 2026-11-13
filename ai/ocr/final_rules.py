"""운영 후처리 v3 (S15P31A109-49·95): 날짜 혼동 문자 보정 + 상호명 v3 + 주소 (도로명 혼동 문자 보정·건물번호).

아래 '옮긴 코드' 부분은 ocr_v3/ocrlab/fields.py (md5 04704bf4, 실험 E9·E13·E18 에서 쓴 코드)에서
그대로 옮겼다. 주석의 '실험 옵션 … 기본 legacy' 설명도 원문 그대로이며, 운영에서는 apply_final_rules 로 항상 켠다.
도로명 혼동 문자 보정(fix_road_lookalikes, E18)도 같은 파일에서 그대로 옮겼다 (S15P31A109-95).
주소는 고친 줄로 address_parser 를 다시 불러 구하고, 건물번호를 읽었으면 건물번호까지 돌려준다.
"""
from __future__ import annotations

import os
import re

import receipt_parser as rp
from address_parser import AddressPolicy, extract_address

# 주소 반환 범위 (S15P31A109-95): building(기본) = 건물번호를 읽었으면 건물번호까지 / road = 도로명까지 (-94 와 같음, 되돌리기용)
ADDRESS_POLICY = AddressPolicy(return_level=os.getenv("OCR_ADDRESS_LEVEL", "building").strip().lower() or "building")


def apply_final_rules(d: dict, lines) -> dict:
    """parse_receipt(lines).to_dict() 결과에 후처리 v3 를 적용한다. ocr_v3 fields.extract 와 같은 순서·조건.
    - 상호명 v3: 찾았을 때만 기존 값을 바꾼다
    - 날짜 혼동 문자 보정: 기존 규칙으로 날짜를 못 찾았을 때만. 시간은 기존 규칙에서도 못 찾았을 때만 채운다
    - 주소: final_address 값으로 바꾼다 (receipt_parser 의 도로명까지 값 대신). 주소 처리 오류는 주소만 null"""
    d = dict(d)
    n3, _ = name_v3(lines)
    if n3 and n3 != d.get("placeName"):
        d["placeName"] = n3
    if d.get("date") is None:
        date2, time2, _ = lookalike_fallback(lines)
        if date2:
            d["date"] = date2
            if d.get("time") is None and time2:
                d["time"] = time2
    try:                                            # 주소 처리 오류가 상호명·날짜·시간 응답을 막지 않게 (receipt_parser 와 같음)
        d["address"], d["addressComplete"] = final_address(lines)
    except Exception:
        d["address"], d["addressComplete"] = None, False
    return d


def final_address(lines, policy: AddressPolicy | None = None):
    """(주소 | None, addressComplete). 도로명 혼동 문자를 고친 줄로 address_parser 를 다시 부른다.
    고친 줄은 주소에만 쓰고 상호명·날짜·시간에는 쓰지 않는다 (ocr_v3 fields.extract 의 addr_lookalike_fix=on 과 같음).
    addressComplete 는 고친 줄 기준으로 도로명과 건물번호를 모두 읽었는지."""
    addr_lines = [(fix_road_lookalikes(t), sc) for t, sc in lines]
    return extract_address(addr_lines, policy or ADDRESS_POLICY)


# ---------------- 옮긴 코드 (ocr_v3/ocrlab/fields.py) ----------------

# 날짜 자리 혼동 문자 보정 (실험 옵션 fields.date_lookalike_fix=fallback, 기본 legacy = 운영과 같음)
# 근거: 11/04 B 모델이 dev2 093452 의 흐린 '0'을 'C'로 읽어 '26-1C-01' → 날짜 형식이 아니라 누락.
# 날짜 모양(연-월-일) 안의 월·일 칸에 있는 글자만 바꾼다. 연도는 숫자만 인정한다.
LOOKALIKE = {"O": "0", "o": "0", "C": "0", "c": "0", "D": "0", "I": "1", "l": "1", "|": "1"}


_LK_GROUP = r"[\d" + re.escape("".join(LOOKALIKE)) + r"]{1,2}"


DATE_SHAPE_RE = re.compile(r"(?<![0-9A-Za-z])(20\d{2}|\d{2})(\s*[.\-/년]\s*)(" + _LK_GROUP + r")(\s*[.\-/월]\s*)(" + _LK_GROUP + r")")


def fix_date_lookalikes(text: str) -> str:
    """'26-1C-01' → '26-10-01'. 월·일 칸에 숫자가 하나 이상 있고 혼동 문자가 있을 때만 바꾼다.
    바꾼 뒤에도 기존 DATE_RE·날짜 범위 검사를 그대로 통과해야 날짜로 쓰인다."""
    def rep(m):
        y, s1, mo, s2, d = m.groups()
        if not any(ch in LOOKALIKE for ch in mo + d):
            return m.group(0)
        if not (any(ch.isdigit() for ch in mo) and any(ch.isdigit() for ch in d)):
            return m.group(0)
        tr = lambda g: "".join(LOOKALIKE.get(ch, ch) for ch in g)
        return y + s1 + tr(mo) + s2 + tr(d)
    return DATE_SHAPE_RE.sub(rep, text)


def lookalike_fallback(lines):
    """기존 규칙으로 날짜를 못 찾았을 때만 부른다. (date, time, 바뀐 줄 번호) — 못 찾으면 date None."""
    fixed = [(fix_date_lookalikes(t), s) for t, s in lines]
    changed = [i for i, (a, b) in enumerate(zip(lines, fixed)) if a[0] != b[0]]
    if not changed:
        return None, None, []
    date, time_, _ = rp.extract_datetime(fixed)
    return date, time_, changed


# ---------- 상호명 선택 v2 (실험 옵션 fields.name_rule=v2, 기본 legacy = 운영과 같음) ----------
# 근거: 11/05 dev2(B30) 상호명 오류 진단. 글자는 맞게 읽었는데 줄을 잘못 자르거나 고른 경우
#   - 상호 뒤에 붙은 'TID:'·'[사업자]'·사업자번호, 줄 앞의 사업자번호·'(--)'·로고 영문 때문에 줄을 버리거나 잘못 자름
#   - '세븐일레븐 (주)코리아세븐 7-ELEVEN 역삼태광점' 처럼 로고·법인명이 끼어 지점명이 잘림
#   - '매장 방문시 가능' 같은 안내문의 '매장'을 상호 라벨로 봄
# 2차(11/05 v2 다시 채점에서 나빠진 6장): 라벨 값의 줄바꿈 이어 붙이기, '(주' → '(주)', 지점명 뒤 꼬리 자르기,
#   줄 중간 '[매장명]' 라벨, 주소 뒤 '상호/사업자번호', 닫는 괄호만 있는 조각·'내점' 같은 한 글자+점 제외
# v2 는 v2 가 상호를 찾았을 때만 기존 값을 바꾼다 (못 찾으면 기존 값 유지).
V2_LABELS = ("상호명", "매장명", "가맹점명", "점포명", "업체명", "상호", "매장", "판매처")


_V2_LABEL_RE = re.compile(r"(?:^|[\s.,])\[?\s*(" + "|".join(V2_LABELS) + r")\s*(?:\]\s*[:：]?|[:：])\s*(.+)$")


_V2_CUT_RE = re.compile(r"\s*(\[|\bTID\b|(?<![가-힣])(?:대표|사업자|TEL|Tel|tel|전화|가맹점번호)\b|/?\s*\d{3}-\d{2}-\d{5}).*$")


_V2_SLASH_BIZ_RE = re.compile(r"^(.*?)\s*/\s*\d{3}-\d{2}-\d{5}")


_V2_BIZ_HEAD_RE = re.compile(r"^\s*\d{3}-\d{2}-\d{5}\s*")


_V2_URL_RE = re.compile(r"(https?://\S+|www\.?\S+|\S+\.co\.?kr\S*|\S+\.com\S*)", re.I)


_V2_HEADER_RE = re.compile(r"상\s*품\s*명|품\s*명|단\s*가|수\s*량|금\s*액")


_V2_JUNK_TOKEN_RE = re.compile(r"^[\(\[\-–—*=_.,:|]+[\)\]\-–—*=_.,:|]*$")


_V2_BRANCH_CUT_RE = re.compile(r"^(.{2,25}?점(?:#\d+|\([^)]*\))?)(?=\s|$)")      # 기존 규칙의 '…점' 뒤 꼬리 자르기


_V2_BRANCH_RE = re.compile(r"[가-힣A-Za-z0-9]{2,}점(?:\b|[#()]|$)")               # '내점' 처럼 한 글자+점은 지점명 아님


_V2_NOISE = ("영수증", "매출전표", "고객용", "가맹점용", "카드판매", "주문번", "대기번", "진동벨", "테이블")


def _v2_clean(text: str, keep_brand_tail: bool = False) -> str:
    t = _V2_URL_RE.sub(" ", text)
    t = _V2_BIZ_HEAD_RE.sub("", t)
    m = _V2_SLASH_BIZ_RE.search(t)                     # 'OO초밥/123-45-67890/홍길동' (기존 v0r2 ②)
    if m:
        head = m.group(1).strip(" ,")
        t = head if rp._looks_like_name(head) else re.split(r"[\s,()]+", head)[-1]   # 주소 뒤에 붙은 상호는 마지막 단어
    t = _V2_CUT_RE.sub("", t)
    t = re.sub(r"[\[\]<>*=]+", " ", t).replace("_", " ")
    toks = [x for x in t.split() if not _V2_JUNK_TOKEN_RE.match(x)]
    has_hangul = any(re.search(r"[가-힣]", x) for x in toks)
    keep = []
    for i, x in enumerate(toks):
        if has_hangul and re.fullmatch(r"[A-Za-z0-9\-.&']+", x) and not re.search(r"[가-힣]", x) and i < len(toks) - 1 \
                and any(re.search(r"[가-힣]", y) for y in toks[i + 1:]):
            continue                                   # 한글 상호 앞·사이의 로고 영문 (emart, 7-ELEVEN)
        if i > 0 and re.match(r"^(\(주\)|㈜)", x) and any(re.search(r"[가-힣]", y) for y in keep):
            continue                                   # 상호 뒤에 끼어든 법인명 '(주)코리아세븐' (맨 앞 법인명은 유지)
        keep.append(x)
    t = " ".join(keep).strip(" -|,")
    m = _V2_BRANCH_CUT_RE.match(t)
    if m and t[m.end():].strip() and not (keep_brand_tail and re.fullmatch(r"[가-힣]{2,}", t[m.end():].strip())):
        t = m.group(1)                                 # '세븐일레븐 역삼태광점#69042 T-ELEVEN' → 지점명까지
    for k in range(2, len(t)):                         # 'OO제과(OO)(OO제과(O' 반복 꼬리 (기존 v0r2 ④)
        if t[k] == "(" and len(t) - k - 1 >= 4 and t.startswith(t[k + 1:]):
            t = t[:k]
            break
    return re.sub(r"^\((?:장|추|쥬|주)(?!\))", "(주)", t.strip())   # 기존 parse_receipt 와 같은 '(주' 보정


def _v2_ok(t: str) -> bool:
    if not t or not rp._looks_like_name(t):
        return False
    if t.count(")") > t.count("("):                    # '역삼점)' '에들라트)1층' 처럼 앞줄에서 넘어온 조각
        return False
    flat = re.sub(r"\s+", "", t)
    return not any(w in flat for w in _V2_NOISE)


# ---------- 상호명 v3 (실험 옵션 fields.name_rule=v3 = v2 + 아래, 기본 legacy = 운영과 같음) ----------
# 근거: 11/09 dev3(구 holdout3, B30) 상호명 진단. 글자는 맞게 읽었는데 v2 가 못 고른 경우
#   ① 카드 전표(KIS 등): '가맹점/사업자번호' 줄 다음 '대표자(TEL:…)' 뒤, 또는 그다음 줄이 상호
#   ② '사업자번호 : 123-45-67890 상호' 처럼 사업자번호 뒤에 상호가 붙은 줄 (위아래 줄이 합쳐짐)
#   ③ '상호 / 사업자번호 / 대표' 에서 사업자번호 자릿수를 덜 읽은 경우 ('629-2-01785')
#   ④ '가맹점명 사업자NO 상호 1234567890 대표자명 : …' 처럼 두 칸 라벨이 한 줄로 합쳐진 경우
#   + 상호가 될 수 없는 안내 문구(할부·승인·정보통신·여신금융·신고안내·교환권·상품명)와 '30-17' 같은 번지는 뺀다
#   + '역삼언주로점 매머드익스프레스' 처럼 지점명 뒤에 한글 브랜드 한 단어가 오면 자르지 않는다 (v2 는 '…점' 까지만 남김)
# v3 도 찾았을 때만 기존 값을 바꾼다. v2 동작은 그대로 둔다.
_V3_CARD_HEAD_RE = re.compile(r"가맹점\s*/\s*사업자\s*번호")


_V3_OWNER_TEL_RE = re.compile(r"^.{0,15}?\(\s*TEL\s*[:：]?[^)]*\)\s*", re.I)


_V3_BIZ_LEAD_RE = re.compile(r"^\s*사업자\s*(?:등록)?\s*번호\s*[:：]?\s*\d{3}\s*-?\s*\d{2}\s*-?\s*\d{5}\s*")


_V3_SLASH_BIZ_RE = re.compile(r"^(.*?)\s*/\s*\d{2,3}-\d{1,2}-\d{4,5}")


_V3_LEAD_LABELS_RE = re.compile(r"^(?:\s*(?:가맹점명|상호명|매장명|사업자\s*(?:등록)?\s*(?:N[Oo]\.?|번호))\s*[:：]?)+")


_V3_LOTNUM_RE = re.compile(r"(?<![\d-])\d{1,4}-\d{1,4}(?![\d-])")


_V3_NOISE = _V2_NOISE + ("할부", "승인", "정보통신", "여신금융", "신고안내", "교환권", "상품명")


def _v3_clean(text: str) -> str:
    t = _V3_BIZ_LEAD_RE.sub("", text)                                  # ②
    m = _V3_SLASH_BIZ_RE.search(t)
    if m and not _V2_SLASH_BIZ_RE.search(t):                            # ③ (자릿수가 맞으면 v2 처리 그대로)
        head = m.group(1).strip(" ,")
        if rp._looks_like_name(head):
            t = head
    if _V3_LEAD_LABELS_RE.match(t):                                     # ④
        rest = re.sub(r"(?<!\d)\d{6,}(?!\d)", " ", _V3_LEAD_LABELS_RE.sub("", t))
        rest = re.split(r"대표", rest)[0]
        if rest.strip():
            t = rest
    return _v2_clean(t, keep_brand_tail=True)


def _v3_ok(t: str) -> bool:
    if not _v2_ok(t) or _V3_LOTNUM_RE.search(t):
        return False
    flat = re.sub(r"\s+", "", t)
    return not any(w in flat for w in _V3_NOISE)


def name_v3(lines, limit: int = 12):
    """(상호 | None, 이유). 카드 전표 가맹점 줄 → 라벨 뒤 값 → 지점명 줄 → 법인 표기 줄 → 첫 후보."""
    texts = [t for t, _ in lines]
    for k, t in enumerate(texts):                                       # ①
        if _V3_CARD_HEAD_RE.search(t):
            # 같은 줄 번호 뒤에 붙은 상호 (줄 묶기 v2 에서 '가맹점/사업자번호 …/123-45-67890 상호' 로 묶일 때) → 다음 줄들
            tail = re.split(r"\d{3}-\d{2}-\d{5}|\d{6,}", t)[-1].strip(" /:")
            nxt = ([tail] if tail and tail != t.strip(" /:") else []) + texts[k + 1:min(k + 4, len(texts))]
            for s in nxt:
                if rp.ADDR_RE.search(s):
                    break
                rest = _V3_OWNER_TEL_RE.sub("", s).strip()
                if not rest:
                    continue
                v = _v2_clean(rest, keep_brand_tail=True)
                if _v3_ok(v):
                    return v, "카드전표 가맹점 줄"
            break
    for i, t in enumerate(texts):
        m = _V2_LABEL_RE.search(t)
        if m:
            v = _v3_clean(rp._join_wrapped(m.group(2), lines, i))
            if _v3_ok(v):
                return v, "라벨 뒤 값"
    stop = next((i for i, t in enumerate(texts) if _V2_HEADER_RE.search(t)), len(texts))
    cands = []
    for i, t in enumerate(texts[:min(limit, stop)]):
        v = _v3_clean(rp._join_wrapped(t, lines, i))
        if _v3_ok(v):
            cands.append(v)
    if not cands:
        return None, "후보 없음"
    for pat, why in ((_V2_BRANCH_RE, "지점명 줄"), (re.compile(r"\(주\)|㈜"), "법인 표기 줄")):
        hit = [c for c in cands if pat.search(c)]
        if hit:
            return hit[0], why
    return cands[0], "첫 후보"


# ---------- 도로명 혼동 문자 보정 (실험 옵션 fields.addr_lookalike_fix=on, 기본 legacy = 운영과 같음) ----------
# 근거: 11/09 dev3 '논현로85일 25' ('길'을 '일'로 읽음) → 도로명이 '논현로', 건물번호 85 로 잘못 잡힘.
# '…로' 바로 뒤 숫자 + '일' + (띄어쓰기) 숫자 모양만 '…로 숫자 길' 로 바꾼다. 주소 추출에만 쓰고 상호·날짜에는 쓰지 않는다.
_ROAD_GIL_RE = re.compile(r"([가-힣]로)(\d{1,3})일(?=\s*\d)")


def fix_road_lookalikes(text: str) -> str:
    return _ROAD_GIL_RE.sub(r"\1\2길", text)
