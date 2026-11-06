"""필드 추출 (실험용 진입점). 상호명·날짜·시간은 운영 코드 receipt_parser 를 그대로 쓰고,
주소는 address_parser(v3), 응답은 ocr_response(v3) 를 쓴다. 기본 설정이면 운영 API 결과와 같다.

진단 정보(diagnostics)에는 후보와 선택 이유가 들어간다. OCR 원문이 포함되므로 API 응답에 넣지 않고,
--diag-out 으로 지정한 파일에만 저장한다.
"""
from __future__ import annotations

import datetime as _dt
import re

import receipt_parser as rp
from address_parser import extract_address_detail
from ocr_response import to_response


def _label_prio(text: str):
    return min((i for i, k in enumerate(rp.DATETIME_LABELS) if k in text), default=None)


def datetime_candidates(lines) -> list[dict]:
    """줄마다 날짜·시간 후보. 형식이 맞는 값만 (월 1~12, 일 1~31, 시 0~23, 분 0~59)."""
    out = []
    for i, (text, _) in enumerate(lines):
        t = rp._normalize_ocr_text(text)
        dates = []
        for m in rp.DATE_RE.finditer(t):
            g = m.groups()
            y, mo, d = (g[0], g[1], g[2]) if g[0] else (g[3], g[4], g[5])
            y = int(y) + (2000 if len(y) == 2 else 0)
            if rp._valid_date(y, int(mo), int(d)):
                dates.append(f"{y:04d}-{int(mo):02d}-{int(d):02d}")
        tm = rp.TIME_RE.search(t)
        time_ = None
        if tm:
            hh, mm = int(tm.group(1)), int(tm.group(2))
            ap = rp.AMPM_RE.search(t)
            if ap and ap.group(1).upper() in ("오후", "PM") and hh < 12:
                hh += 12
            if ap and ap.group(1).upper() in ("오전", "AM") and hh == 12:
                hh = 0
            time_ = f"{hh:02d}:{mm:02d}"
        if dates or time_:
            p = _label_prio(t)
            out.append({"line": i, "dates": dates, "time": time_, "label": None if p is None else rp.DATETIME_LABELS[p]})
    return out


def _ambiguous(cands, key, chosen) -> bool:
    """서로 다른 값이 둘 이상이고, 고른 값이 라벨 있는 줄에서 나온 것이 아니면 '근거 없음'."""
    vals = {v for c in cands for v in (c["dates"] if key == "date" else [c["time"]]) if v}
    if chosen is None or len(vals) < 2:
        return False
    return not any(c["label"] and chosen in (c["dates"] if key == "date" else [c["time"]]) for c in cands)


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


def _v2_clean(text: str) -> str:
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
    if m and t[m.end():].strip():
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


def name_v2(lines, limit: int = 12):
    """(상호 | None, 이유). 라벨('상호:'·'[매장명]' 뒤 값) → 지점명(…점) 줄 → 법인 표기 줄 → 첫 후보."""
    texts = [t for t, _ in lines]
    for i, t in enumerate(texts):
        m = _V2_LABEL_RE.search(t)
        if m:
            v = _v2_clean(rp._join_wrapped(m.group(2), lines, i))
            if _v2_ok(v):
                return v, "라벨 뒤 값"
    stop = next((i for i, t in enumerate(texts) if _V2_HEADER_RE.search(t)), len(texts))
    cands = []
    for i, t in enumerate(texts[:min(limit, stop)]):
        v = _v2_clean(rp._join_wrapped(t, lines, i))
        if _v2_ok(v):
            cands.append(v)
    if not cands:
        return None, "후보 없음"
    for pat, why in ((_V2_BRANCH_RE, "지점명 줄"), (re.compile(r"\(주\)|㈜"), "법인 표기 줄")):
        hit = [c for c in cands if pat.search(c)]
        if hit:
            return hit[0], why
    return cands[0], "첫 후보"


def calendar_ok(date: str | None) -> bool:
    if date is None:
        return True
    try:
        _dt.date.fromisoformat(date)
        return True
    except ValueError:
        return False


def name_candidates(lines, limit: int = 12) -> list[dict]:
    """상호명 후보와 이유 (receipt_parser 규칙을 그대로 따라 표시만 한다)."""
    out = []
    for i, (text, _) in enumerate(lines):
        v = rp._after_label(text, rp.PLACE_LABELS)
        if v:
            out.append({"line": i, "value": v, "reason": "상호명 라벨 뒤 값 (1순위)"})
    for i, (text, _) in enumerate(lines[:limit]):
        cleaned = re.sub(r"\s+", " ", re.sub(r"[\[\]<>*=]+", " ", text).strip(" -").replace("_", " "))
        if hasattr(rp, "_clean_name_v0r2"):
            cleaned = rp._clean_name_v0r2(cleaned)
        if rp._looks_like_name(cleaned):
            why = ("지점명(…점) 줄" if re.search(r"점\b|점[#(]|점\)", cleaned)
                   else "법인 표기 줄" if re.search(r"\(주\)|㈜", cleaned) else "상단 이름 모양 줄")
            out.append({"line": i, "value": cleaned, "reason": why})
    return out


def extract(lines, cfg: dict) -> tuple[dict, dict]:
    """lines: [(text, score)] (group_lines 결과). 반환: (API 응답 dict, 진단 dict)"""
    from .settings import address_policy
    r = rp.parse_receipt(lines)
    d = r.to_dict()
    pol = address_policy(cfg)
    addr = extract_address_detail(lines, pol)
    d["address"], d["addressComplete"] = addr.address, addr.complete

    dt_c = datetime_candidates(lines)
    diag = {"datetime": {"candidates": dt_c, "selected": {"date": d["date"], "time": d["time"]},
                         "ambiguous_date": _ambiguous(dt_c, "date", d["date"]),
                         "ambiguous_time": _ambiguous(dt_c, "time", d["time"]), "actions": []},
            "placeName": {"selected": d["placeName"], "candidates": name_candidates(lines)},
            "address": addr.diagnostics(), "policy": {"fields": dict(cfg["fields"]), "address": dict(cfg["address"])}}
    f = cfg["fields"]
    if f.get("name_rule", "legacy") == "v2":
        n2, why = name_v2(lines)
        diag["placeName"]["v2"] = {"value": n2, "reason": why}
        if n2 and n2 != d["placeName"]:
            diag["placeName"]["action"] = f"상호명 v2 ({why}) 로 변경"
            d["placeName"] = n2
    if f.get("date_lookalike_fix", "legacy") == "fallback" and d["date"] is None:   # 이미 찾은 날짜는 바꾸지 않는다
        date2, time2, changed = lookalike_fallback(lines)
        if date2:
            d["date"] = date2
            diag["datetime"]["actions"].append(f"날짜 자리 혼동 문자 보정 (줄 {changed}) → date {date2}")
            if d["time"] is None and time2:                   # 기존 규칙에서 시간도 못 찾았을 때만 채운다
                d["time"] = time2
                diag["datetime"]["actions"].append(f"같은 보정으로 time {time2}")
    if f["date_validation"] == "calendar" and not calendar_ok(d["date"]):
        diag["datetime"]["actions"].append(f"달력에 없는 날짜 {d['date']} → null")
        d["date"] = None
    if f["datetime_ambiguity"] == "null_if_ambiguous":
        for k in ("date", "time"):
            if diag["datetime"][f"ambiguous_{k}"] and d[k] is not None:
                diag["datetime"]["actions"].append(f"{k} 후보가 여럿이고 라벨 근거 없음 → null")
                d[k] = None
    return to_response(d), diag
