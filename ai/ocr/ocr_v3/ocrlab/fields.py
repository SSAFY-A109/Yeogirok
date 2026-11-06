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
    if f["date_validation"] == "calendar" and not calendar_ok(d["date"]):
        diag["datetime"]["actions"].append(f"달력에 없는 날짜 {d['date']} → null")
        d["date"] = None
    if f["datetime_ambiguity"] == "null_if_ambiguous":
        for k in ("date", "time"):
            if diag["datetime"][f"ambiguous_{k}"] and d[k] is not None:
                diag["datetime"]["actions"].append(f"{k} 후보가 여럿이고 라벨 근거 없음 → null")
                d[k] = None
    return to_response(d), diag
