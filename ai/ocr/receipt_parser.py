"""영수증 OCR 결과(텍스트 + 박스)에서 장소와 시간만 뽑아내는 파서.

PaddleOCR에 의존하지 않아서 단독으로 테스트할 수 있다.
"""
from __future__ import annotations

import math
import re
from dataclasses import dataclass, asdict
from typing import Optional

# ---------- 1. 박스 -> 줄 묶기 ----------

@dataclass
class Box:
    text: str
    score: float
    x: float      # 왼쪽 x
    cy: float     # 세로 중심
    h: float      # 높이
    cx: float = 0.0     # 가로 중심
    angle: float = 0.0  # 박스 윗변 기울기(라디안)


def boxes_from_polys(texts, scores, polys) -> list[Box]:
    out = []
    for t, s, p in zip(texts, scores, polys):
        xs = [pt[0] for pt in p]
        ys = [pt[1] for pt in p]
        ang = math.atan2(p[1][1] - p[0][1], p[1][0] - p[0][0]) if len(p) >= 2 and p[1][0] != p[0][0] else 0.0
        out.append(Box(t, float(s), min(xs), sum(ys) / len(ys), max(ys) - min(ys),
                       sum(xs) / len(xs), ang))
    return out


def group_lines(boxes: list[Box]) -> list[tuple[str, float]]:
    """y 좌표가 비슷한 박스를 한 줄로 합친다. (텍스트, 최소 신뢰도) 리스트를 반환.
    사진이 기울어 있으면 왼쪽 라벨과 오른쪽 값의 y가 어긋나므로, 박스들의 기울기 중앙값으로 보정한 y를 쓴다."""
    if not boxes:
        return []
    angs = sorted(b.angle for b in boxes if b.cx and abs(b.angle) < 0.35)
    skew = angs[len(angs) // 2] if angs else 0.0
    t = math.tan(skew)
    boxes = [Box(b.text, b.score, b.x, b.cy - t * b.cx, b.h, b.cx, b.angle) for b in boxes]
    boxes = sorted(boxes, key=lambda b: b.cy)
    lines: list[list[Box]] = []
    for b in boxes:
        if lines:
            last = lines[-1]
            ref_cy = sum(x.cy for x in last) / len(last)
            ref_h = max(x.h for x in last)
            if abs(b.cy - ref_cy) < max(ref_h, b.h) * 0.5:
                last.append(b)
                continue
        lines.append([b])
    result = []
    for line in lines:
        line.sort(key=lambda b: b.x)
        result.append((" ".join(b.text for b in line).strip(), min(b.score for b in line)))
    return result


# ---------- 2. 정규식 ----------

DATE_RE = re.compile(
    r"(?<!\d)(20\d{2}|\d{2})\s*[.\-/년]\s*(\d{1,2})\s*[.\-/월]\s*(\d{1,2})(?!\d)"
    r"|(?<!\d)(20\d{2})(0[1-9]|1[0-2])(0[1-9]|[12]\d|3[01])(?!\d)"
)
TIME_RE = re.compile(r"(?<!\d)([01]?\d|2[0-3])\s*(?::|시)\s*([0-5]\d)(?:\s*(?::|분)\s*([0-5]\d))?(?!\d)")
AMPM_RE = re.compile(r"(오전|오후|AM|PM)", re.I)
# 앞에 있을수록 우선 (판매·거래 일시를 카드 승인일시보다 먼저 본다)
DATETIME_LABELS = ["거래일시", "판매일시", "판매시간", "매출일", "판매일", "결제일시", "주문일시", "거래일", "[판", "정상", "구매", "승인일시", "일시", "날짜"]

PLACE_LABELS = ["상호명", "매장명", "가맹점명", "점포명", "업체명", "상호", "매장", "판매처"]
ADDR_LABELS = ["주소", "소재지"]
REGION = r"(서울|부산|대구|인천|광주|대전|울산|세종|경기|강원|충청|충북|충남|전라|전북|전남|경상|경북|경남|제주)"
ADDR_RE = re.compile(REGION + r"\S*\s+.*?(로|길|동|읍|면|리)\s*[\d\-]*")

NOISE_WORDS = [
    "영수증", "매출전표", "신용카드", "카드", "전화", "TEL", "Tel", "사업자", "대표", "주소",
    "POS", "포스", "번호", "합계", "금액", "부가세", "과세", "승인", "고객용", "가맹점용",
    "감사합니다", "www", "http", "SYNTHETIC", "테스트용", "진동벨", "주문번호", "모니터", "와이파이",
    "고객용", "영수증 ]", "[영수증]", "카드판매", "사업", "DATE", "Date",
]


# ---------- 3. 추출 ----------

def _valid_date(y: int, m: int, d: int) -> bool:
    return 2000 <= y <= 2099 and 1 <= m <= 12 and 1 <= d <= 31


# OCR이 날짜와 시간 사이 공백을 지우는 경우 보정: '26-03-1321:51' -> '26-03-13 21:51'
GLUED_DT_RE = re.compile(r"(\d{2,4}[.\-/]\d{1,2}[.\-/]\d{2})(\d{1,2}:\d{2})")


def _normalize_ocr_text(text: str) -> str:
    return GLUED_DT_RE.sub(r"\1 \2", text)


def extract_datetime(lines: list[tuple[str, float]]):
    """(date 'YYYY-MM-DD' | None, time 'HH:MM' | None, score)"""
    lines = [(_normalize_ocr_text(t), s) for t, s in lines]
    # 라벨이 있는 줄을 먼저 보고, 그다음 나머지 줄을 본다.
    def prio(text):
        return min((i for i, k in enumerate(DATETIME_LABELS) if k in text), default=None)
    labeled = sorted((l for l in lines if prio(l[0]) is not None), key=lambda l: prio(l[0]))
    ordered = labeled + [l for l in lines if l not in labeled]

    best_date = best_time = None
    best_score = 0.0
    for text, score in ordered:
        date = time = None
        for m in DATE_RE.finditer(text):
            g = m.groups()
            y, mo, d = (g[0], g[1], g[2]) if g[0] else (g[3], g[4], g[5])
            y = int(y) + (2000 if len(y) == 2 else 0)
            if _valid_date(y, int(mo), int(d)):
                date = f"{y:04d}-{int(mo):02d}-{int(d):02d}"
                break
        tm = TIME_RE.search(text)
        if tm:
            hh, mm = int(tm.group(1)), int(tm.group(2))
            ap = AMPM_RE.search(text)
            if ap and ap.group(1).upper() in ("오후", "PM") and hh < 12:
                hh += 12
            if ap and ap.group(1).upper() in ("오전", "AM") and hh == 12:
                hh = 0
            time = f"{hh:02d}:{mm:02d}"

        # 날짜와 시간이 같은 줄에 있으면 그게 정답일 확률이 가장 높다.
        if date and time:
            return date, time, score
        if date and not best_date:
            best_date, best_score = date, score
        if time and not best_time:
            best_time = time
    return best_date, best_time, best_score


def _after_label(text: str, labels: list[str]) -> Optional[str]:
    for k in labels:
        idx = text.find(k)
        if idx != -1:
            after = text[idx + len(k):]
            if not after[:1] or after[:1] not in " :：]":
                continue                      # '가맹점번호' 처럼 라벨 뒤에 다른 글자가 붙은 경우 제외
            rest = after.lstrip(" :：])").strip()
            rest = re.split(r"\s*/\s*\d{3}-\d{2}-\d{5}", rest)[0].strip()   # '카페/123-45-67890'
            if re.fullmatch(r"[\d\-\s:*#]+", rest):
                continue
            # "상호: OO식당 대표: 홍길동" 같은 경우 다음 라벨 앞에서 자른다.
            rest = re.split(r"\s(대표|사업자|TEL|전화|Tel)", rest)[0].strip()
            if len(rest) >= 2:
                return rest
    return None


def _looks_like_name(text: str) -> bool:
    if text.strip().upper() in ("DATE", "NO", "NO."):
        return False
    if re.search(r"\d{6,}", text):   # 사업자번호·전화번호처럼 긴 숫자가 있는 줄은 상호명 아님
        return False
    if re.fullmatch(r"[\d.\s]*\(\S{1,6}(동|읍|면|리|가)\)", text):
        return False
    if ADDR_RE.search(text):          # 주소 줄은 상호명 아님
        return False
    if any(w in text for w in NOISE_WORDS):
        return False
    if DATE_RE.search(text) or TIME_RE.search(text):
        return False
    letters = re.findall(r"[가-힣A-Za-z]", text)
    return len(letters) >= 2 and len(text) <= 30


def _join_wrapped(text: str, lines, i: int) -> str:
    """'(주)럭키엠케이유(럭키할인마트' + 다음 줄 '역삼점)' 처럼 괄호가 안 닫힌 상호명은 다음 짧은 줄을 이어 붙인다."""
    if text.count("(") > text.count(")") and i + 1 < len(lines):
        nxt = lines[i + 1][0].strip()
        if ")" in nxt and len(nxt) <= 15 and not re.search(r"\d{3,}", nxt):
            return f"{text} {nxt}"
    return text


def extract_place(lines: list[tuple[str, float]]):
    """(place_name | None, address | None, score)"""
    name = address = None
    name_score = 0.0

    for i, (text, score) in enumerate(lines):
        if not name:
            v = _after_label(text, PLACE_LABELS)
            if v:
                name, name_score = _join_wrapped(v, lines, i), score
        if not address:
            v = _after_label(text, ADDR_LABELS)
            if v and ADDR_RE.search(v):
                address = v
            else:
                m = ADDR_RE.search(text)
                if m:
                    address = text[m.start():].strip()

    # 라벨이 없으면 영수증 상단 8줄에서 상호명처럼 생긴 줄을 고른다.
    # 지점명('OO점')이나 법인 표기('(주)')가 있는 줄을 로고·안내 문구보다 우선한다.
    if not name:
        cands = []
        for i, (text, score) in enumerate(lines[:12]):
            text = _join_wrapped(text, lines, i)
            cleaned = re.sub(r"[\[\]<>*=]+", " ", text).strip(" -")
            cleaned = re.sub(r"\s+", " ", cleaned.replace("_", " "))
            m = re.match(r"^(.{2,25}?점(?:#\d+|\([^)]*\))?)(?=\s|$)", cleaned)
            if m and cleaned[m.end():].strip():
                cleaned = m.group(1)
            if _looks_like_name(cleaned):
                cands.append((cleaned, score))
        if cands:
            branch = [c for c in cands if re.search(r"점\b|점[#(]|점\)", c[0])]   # 'OO점' 지점명 줄이 1순위
            corp = [c for c in cands if re.search(r"\(주\)|㈜", c[0])]            # 법인 표기 줄이 2순위
            name, name_score = (branch or corp or cands)[0]
    return name, address, name_score


@dataclass
class ReceiptResult:
    placeName: Optional[str]
    address: Optional[str]
    date: Optional[str]
    time: Optional[str]
    visitedAt: Optional[str]   # ISO 8601, KST
    confidence: str            # "high" | "low"
    rawLines: list[str]

    def to_dict(self):
        return asdict(self)


def parse_receipt(lines: list[tuple[str, float]], min_score: float = 0.8) -> ReceiptResult:
    name, address, name_score = extract_place(lines)
    if name:   # OCR이 '㈜'를 '(장', '(추' 등으로 읽는 경우 보정
        name = re.sub(r"^\((?:장|추|쥬|주)(?!\))", "(주)", name)
    date, time, dt_score = extract_datetime(lines)
    visited_at = None
    if date:
        visited_at = f"{date}T{time or '00:00'}:00+09:00"
    ok = name and date and time and name_score >= min_score and dt_score >= min_score
    return ReceiptResult(
        placeName=name,
        address=address,
        date=date,
        time=time,
        visitedAt=visited_at,
        confidence="high" if ok else "low",
        rawLines=[t for t, _ in lines],
    )
