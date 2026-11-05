#!/usr/bin/env python3
"""합성 영수증 이미지 + 정답 JSON 생성기 (OCR 개발·검증용).

원본 영수증 10장의 '레이아웃 형식'만 참고해서 템플릿 10개(T01~T10)를 만들고,
템플릿마다 가상 거래 20건(쉬움 8 / 보통 8 / 어려움 4)을 렌더링한다.
모든 상호·주소·전화번호·사업자번호·카드번호·승인번호는 난수로 만든 가상 값이다.

실행:  python generate_receipts.py --out . --seed 20261027
"""
from __future__ import annotations

import argparse
import csv
import glob
import hashlib
import json
import math
import os
import random
import shutil
import zipfile
from dataclasses import dataclass, field
from datetime import datetime, timedelta

import cv2
import numpy as np
from PIL import Image, ImageDraw, ImageFont

DEFAULT_SEED = 20261027
PER_TEMPLATE = 20
DIFF_PLAN = ["easy"] * 8 + ["medium"] * 8 + ["hard"] * 4   # 템플릿마다 동일 → 난이도 편중 없음
ITEM_COUNT_PLAN = [1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 8, 9, 10, 11, 12, 14, 15]
SYN_MARK = "SYNTHETIC / 테스트용"

# --------------------------------------------------------------------------------------
# 폰트
# --------------------------------------------------------------------------------------
FONT_DIRS = [os.environ.get("RECEIPT_FONT_DIR", ""), "/usr/share/fonts/opentype/noto",
             "/usr/share/fonts/noto-cjk", "/usr/share/fonts/google-noto-cjk",
             "/System/Library/Fonts", "C:/Windows/Fonts"]


def _find(name):
    for d in FONT_DIRS:
        if d and os.path.exists(os.path.join(d, name)):
            return os.path.join(d, name)
    hits = glob.glob(f"/usr/share/fonts/**/{name}", recursive=True)
    if hits:
        return hits[0]
    raise FileNotFoundError(
        f"폰트 {name} 를 찾을 수 없습니다. 'sudo apt install fonts-noto-cjk' 후 다시 실행하거나 "
        f"RECEIPT_FONT_DIR 환경변수로 NotoSansCJK-*.ttc 위치를 지정하세요.")


FONT_FILES = {
    # (파일, ttc index)  index 1 = KR, index 6 = Mono KR
    ("mono", False): ("NotoSansCJK-Regular.ttc", 6),
    ("mono", True): ("NotoSansCJK-Bold.ttc", 6),
    ("sans", False): ("NotoSansCJK-Regular.ttc", 1),
    ("sans", True): ("NotoSansCJK-Bold.ttc", 1),
    ("serif", False): ("NotoSerifCJK-Regular.ttc", 1),
    ("serif", True): ("NotoSerifCJK-Bold.ttc", 1),
}
_font_cache: dict = {}


def font(kind="mono", size=22, bold=False):
    key = (kind, size, bold)
    if key not in _font_cache:
        if kind == "script":   # 가상 몰 로고용 라틴 이탤릭
            path = None
            for n in ["DejaVuSerif-BoldItalic.ttf", "DejaVuSerif-Italic.ttf", "FreeSerifBoldItalic.ttf"]:
                try:
                    path = _find(n)
                    break
                except FileNotFoundError:
                    pass
            _font_cache[key] = ImageFont.truetype(path, size) if path else font("serif", size, True)
        else:
            fname, idx = FONT_FILES[(kind, bold)]
            _font_cache[key] = ImageFont.truetype(_find(fname), size, index=idx)
    return _font_cache[key]


# --------------------------------------------------------------------------------------
# 렌더러
# --------------------------------------------------------------------------------------
W = 600            # 영수증 폭(px) ≈ 80mm 감열지 @ 190dpi
X0, X1 = 30, 570   # 인쇄 영역


@dataclass
class Renderer:
    paper: tuple
    ink: tuple
    rng: random.Random
    y: int = 34
    lines: list = field(default_factory=list)
    keys: dict = field(default_factory=dict)

    def __post_init__(self):
        self.img = Image.new("RGB", (W, 9000), self.paper)
        self.d = ImageDraw.Draw(self.img)

    # 한 줄에 여러 조각을 배치: segs = [(text, align('l'|'r'|'c'), x)]
    def line(self, segs, size=21, kind="mono", bold=False, gap=1.18, key=None,
             underline=False, invert=False, color=None, record=True):
        f = font(kind, size, bold)
        asc, desc = f.getmetrics()
        h = asc + desc
        placed = []
        for text, align, x in segs:
            if text == "":
                continue
            w = self.d.textlength(text, font=f)
            x0 = x if align == "l" else (x - w if align == "r" else x - w / 2)
            x0 = max(4, min(x0, W - 4 - w))
            if invert:
                self.d.rectangle([x0 - 4, self.y + 2, x0 + w + 4, self.y + h], fill=self.ink)
                self.d.text((x0, self.y), text, font=f, fill=self.paper)
            else:
                self.d.text((x0, self.y), text, font=f, fill=color or self.ink)
            placed.append((x0, text, (x0, self.y, x0 + w, self.y + h)))
        if underline and placed:
            self.d.line([(X0, self.y + h), (X1, self.y + h)], fill=self.ink, width=2)
        placed.sort(key=lambda p: p[0])
        if record and placed:
            self.lines.append(" ".join(p[1] for p in placed))
        if key and placed:
            bx = [p[2] for p in placed]
            box = (min(b[0] for b in bx), min(b[1] for b in bx), max(b[2] for b in bx), max(b[3] for b in bx))
            self.keys.setdefault(key, []).append(box)
        self.y += int(h * gap)

    def text(self, s, align="l", **kw):
        x = {"l": X0, "r": X1, "c": W / 2}[align]
        self.line([(s, align, x)], **kw)

    def space(self, px=10):
        self.y += px

    def sep(self, ch="-", size=19):
        f = font("mono", size)
        n = int((X1 - X0) / self.d.textlength(ch, font=f))
        self.line([(ch * n, "l", X0)], size=size, gap=1.0)

    def wrap(self, text, maxw, size=21, kind="mono", bold=False):
        f = font(kind, size, bold)
        out, cur = [], ""
        for ch in text:
            if self.d.textlength(cur + ch, font=f) > maxw and cur:
                # 공백에서 끊을 수 있으면 공백 기준으로
                if " " in cur and not cur.endswith(" "):
                    head, tail = cur.rsplit(" ", 1)
                    out.append(head)
                    cur = tail + ch
                else:
                    out.append(cur)
                    cur = ch
            else:
                cur += ch
        if cur:
            out.append(cur)
        return [o.strip() for o in out if o.strip()]

    def barcode(self, digits, height=64, width=380):
        rr = random.Random(digits)
        x = W / 2 - width / 2
        while x < W / 2 + width / 2:
            bw = rr.choice([2, 2, 3, 4, 5])
            if rr.random() < 0.55:
                self.d.rectangle([x, self.y, x + bw - 1, self.y + height], fill=self.ink)
            x += bw
        self.y += height + 4
        self.text(digits, "c", size=19)

    def qr(self, label=None, n=25, cell=7):
        rr = random.Random(self.rng.random())
        size = n * cell
        x0 = int(W / 2 - size / 2)
        y0 = self.y
        for i in range(n):
            for j in range(n):
                if rr.random() < 0.5:
                    self.d.rectangle([x0 + j * cell, y0 + i * cell, x0 + (j + 1) * cell - 1, y0 + (i + 1) * cell - 1],
                                     fill=self.ink)
        for (fi, fj) in [(0, 0), (0, n - 7), (n - 7, 0)]:    # 찾기 패턴 모양(의미 없는 무작위 패턴)
            ax, ay = x0 + fj * cell, y0 + fi * cell
            self.d.rectangle([ax, ay, ax + 7 * cell - 1, ay + 7 * cell - 1], fill=self.paper)
            self.d.rectangle([ax, ay, ax + 7 * cell - 1, ay + 7 * cell - 1], outline=self.ink, width=cell)
            self.d.rectangle([ax + 2 * cell, ay + 2 * cell, ax + 5 * cell - 1, ay + 5 * cell - 1], fill=self.ink)
        self.y += size + 10

    def mark(self):
        self.text(SYN_MARK, "c", size=15, kind="sans", color=(120, 120, 120), gap=1.4)

    def finish(self):
        self.space(18)
        self.mark()
        self.space(26)
        return self.img.crop((0, 0, W, self.y)), self.lines, self.keys


# --------------------------------------------------------------------------------------
# 가상 데이터 풀
# --------------------------------------------------------------------------------------
REGIONS = [  # (시/도 표기, 구/시, 지역번호)
    ("서울특별시", "강남구", "02"), ("서울특별시", "마포구", "02"), ("서울특별시", "종로구", "02"),
    ("서울특별시", "성동구", "02"), ("서울", "구로구", "02"), ("서울", "송파구", "02"),
    ("부산광역시", "해운대구", "051"), ("부산", "중구", "051"), ("강원특별자치도", "강릉시", "033"),
    ("강원", "속초시", "033"), ("전북특별자치도", "전주시 완산구", "063"), ("경북", "경주시", "054"),
    ("전남", "여수시", "061"), ("제주특별자치도", "제주시", "064"), ("제주", "서귀포시", "064"),
    ("인천광역시", "중구", "032"), ("대구광역시", "중구", "053"), ("경기도", "수원시 팔달구", "031"),
]
ROADS = ["한빛로", "누리로", "은하수로", "솔바람길", "가온로", "다온로", "새빛로", "푸른숲길", "별무리로",
         "햇살로", "바다마루로", "하늬바람길", "도담로", "온새미로", "늘봄로", "미리내로"]
DONGS = ["가람동", "나래동", "다솜동", "마루동", "새봄동", "아라동", "하늘동", "온누리동"]
BRANCHES = ["역삼", "삼성", "성수", "합정", "연남", "광화문", "해운대", "서면", "전주한옥", "경주황리단",
            "강릉중앙", "속초해변", "여수낭만", "애월", "서귀포", "송도", "동성로", "수원역", "한빛타워", "누리센트럴"]
SURNAMES = list("김이박최정강조윤장임한오서신권황안송류홍")
GIVEN = ["가람", "나래", "다온", "라온", "마루", "바다", "새봄", "아라", "하늘", "도담", "보람", "한결",
         "슬기", "온유", "단비", "이든", "해솔", "가온"]
CARD_COS = ["하늘카드", "바다카드", "누리카드", "한빛카드", "별빛카드", "가온카드", "미리내카드"]
PAY_APPS = ["누리페이", "하늘페이", "바로페이"]

ITEMS = {
    "mall_snack": [("스틱/크림치즈", 4800), ("스틱/고구마크림치즈", 6000), ("스틱/아몬드크림", 6200),
                   ("스틱/아몬드크림치즈", 6000), ("프레첼/오리지널", 4300), ("프레첼/시나몬슈가", 4800),
                   ("프레첼/갈릭버터", 5200), ("레모네이드(L)", 5500), ("아이스티(R)", 4500),
                   ("스틱/할라피뇨치즈", 5800), ("미니프레첼 6입 박스", 9800), ("스틱/블루베리크림", 6200)],
    "mall_long": ["스틱/트리플초코크림치즈(시즌한정)", "프레첼 패밀리박스(오리지널+시나몬+갈릭)"],
    "cafe": [("아메리카노(ICE)", 4500), ("아메리카노(HOT)", 4000), ("카페라떼", 5000), ("바닐라라떼(ICE)", 5500),
             ("콜드브루", 5300), ("캐모마일", 4000), ("얼그레이", 4000), ("복숭아아이스티", 3000),
             ("제로슈가 복숭아아이스티", 3000), ("(S)ICE드립커피", 5000), ("치킨치아바타", 5500),
             ("햄치즈샌드위치", 5900), ("초코머핀", 3800), ("크로플", 4500), ("레몬마들렌", 2800),
             ("카라멜마키아또", 5800), ("녹차라떼", 5300), ("자몽에이드", 5500)],
    "cafe_long": ["딸기요거트스무디(휘핑추가,시럽적게)", "시즌한정 흑임자크림라떼(ICE,오트밀크변경)",
                  "제주말차 크림프라푸치노 그란데 사이즈"],
    "eel": [("장어구이 특상(1마리)", 36500), ("장어덮밥", 21000), ("공기밥", 1000), ("된장찌개", 3000),
            ("장어탕", 12000), ("계란찜", 5000), ("소주", 5000), ("맥주", 6000), ("음료수", 2000),
            ("장어구이 보통(1마리)", 29000), ("우동사리", 3000)],
    "eel_long": ["히쓰마부시 특상 특 장어(1마리) 세트메뉴", "민물장어 소금구이 2인 정식(장어탕포함)"],
    "korean": [("고등어구이", 13000), ("된장찌개", 9000), ("김치찌개", 9000), ("제육볶음", 11000),
               ("순두부찌개", 9500), ("계란말이", 8000), ("공기밥", 1000), ("콜라", 2000),
               ("갈치조림", 16000), ("부대찌개(1인)", 10000), ("두부김치", 12000)],
    "korean_long": ["차돌된장찌개(2인분)+계란말이 세트", "옛날식 고등어 김치찜 대(3~4인분)"],
    "mart": [("한빛)대란 30구", 8980, True), ("누리)알뜰사각어묵", 2980, False), ("바른)바몬드카레", 3000, False),
             ("청솔)맛소금 1kg", 1000, False), ("한빛)저지방우유 1L", 1200, True), ("누리)미니버터 64g", 3750, False),
             ("바른)스노우크랩", 2980, False), ("햇살)국산콩두부 300g", 1980, True), ("다온)생수 2L 6입", 4980, False),
             ("농협)바나나 1송이", 3980, True), ("한빛)참치캔 3입", 6980, False), ("누리)라면 5입", 4280, False),
             ("햇살)양파 1.5kg", 3480, True), ("바른)부침가루 1kg", 2780, False)],
    "mart_long": ["한빛)국산콩 부드러운두부 300g 2입 기획", "누리)프리미엄 순살치킨너겟 1kg 대용량"],
    "cvs": [("햄치즈샌드위치", 2900), ("삼각김밥 참치마요", 1500), ("바나나우유", 1800), ("생수 500ml", 1000),
            ("컵라면 큰사발", 1600), ("아이스아메리카노", 1500), ("초코바", 1300), ("제육도시락", 5200),
            ("콜라 500ml", 2200), ("감자칩 오리지널", 1700), ("숙취해소음료", 4500)],
    "cvs_long": ["한빛)대만식햄치즈샌드위치 더블업 에디션", "누리)매콤제육 불고기 도시락(밥곱빼기)"],
}

BRANDS = {
    "T01": ["프레첼하우스", "버터스틱공방", "달콤프레첼", "소금빵연구소", "츄러스팩토리", "허니브레드랩"],
    "T02": ["(주)빈스타코리아", "(주)원두마을", "(주)블루빈커피", "(주)로스터리하우스"],
    "T03": ["바나바나커피", "하루커피", "데일리샷커피", "컵앤컵"],
    "T04": ["우나정", "장어마을", "강변장어", "숯불장어명가"],
    "T05": ["커피구름", "커피나무숲", "카페소소", "빈앤빈"],
    "T06": ["행복할인마트", "알뜰마트", "이웃마트", "온가족마트"],
    "T07": ["하루24", "올데이24", "이음24"],
    "T08": ["옛날찌개집", "두부한상", "한끼식당", "골목밥집"],
    "T09": ["행복할인마트", "알뜰마트", "이웃마트", "온가족마트"],
    "T10": ["카페모아", "카페온", "카페봄날", "카페여름"],
}
SOURCES = {
    "T01": "원본1: 쇼핑몰 입점 매장 영수증(몰 로고, 상품명/수량/금액, 소계·과세가액·부가세·합계, 바코드+QR)",
    "T02": "원본2: 카페 POS(진동벨 번호, 정상 일시, 단가/수량/금액+옵션행, 결제금액 강조, 카드정보 블록)",
    "T03": "원본3: 키오스크 카페(큰 주문번호, 와이파이, 거래일시 년월일시분초, 주문내역 '이름(n개) 금액원')",
    "T04": "원본4: 음식점 카드판매 영수증(테이블명, 판매시간 YYYYMMDD, 품목명 다음 줄에 숫자, '::' 라벨)",
    "T05": "원본5: 키오스크 카페(작은 상호+큰 주문번호, 결제정보 먼저, 하단 상호정보 우측정렬)",
    "T06": "원본6: 할인마트(브랜드)품목, */# 표시, 할인·면세·과세·부가세, 판매일 YY-MM-DD+요일)",
    "T07": "원본7: 편의점(굵은 로고, 품목명+상품코드 줄, 합계 ₩, 승인정보, 영문 안내문)",
    "T08": "원본8: 음식점 POS([대괄호] 라벨, 받을금액/받은금액 강조, 신용승인정보 블록)",
    "T09": "원본9: 할인마트(원본6과 같은 POS 형식의 다른 영수증 → 같은 레이아웃 계열, 포인트 블록 변형)",
    "T10": "원본10: 카페 POS([영수증] 제목, 반전 주문번호, ▶옵션행, 회원 마일리지 블록)",
}
WEEK = "월화수목금토일"


def won(n):
    return f"{n:,}"


class Fake:
    """가상 식별자 생성기. 실존 정보와 겹치지 않도록 '유효하지 않은' 형식을 일부러 사용."""

    def __init__(self, rng):
        self.r = rng

    def digits(self, n):
        return "".join(str(self.r.randint(0, 9)) for _ in range(n))

    def biz_no(self):
        # 국세청 사업자번호 검증식과 '불일치'하는 마지막 자리 → 유효한 번호가 될 수 없음
        d = [self.r.randint(0, 9) for _ in range(9)]
        w = [1, 3, 7, 1, 3, 7, 1, 3, 5]
        s = sum(a * b for a, b in zip(d, w)) + (d[8] * 5) // 10
        valid = (10 - s % 10) % 10
        last = (valid + self.r.randint(1, 9)) % 10
        n = "".join(map(str, d)) + str(last)
        return f"{n[:3]}-{n[3:5]}-{n[5:]}"

    def phone(self, area):
        # 국번이 0으로 시작 → 개통될 수 없는 번호
        return f"{area}-0{self.digits(2 if area != '02' else 3)}-{self.digits(4)}"

    def person(self):
        return self.r.choice(SURNAMES) + self.r.choice(GIVEN)

    def card_masked(self, style):
        # 카드번호는 0으로 시작(발급될 수 없는 BIN) + 마스킹
        head = "0" + self.digits(3)
        mid = self.digits(2)
        if style == "dash":
            return f"{head}-{mid}**-****-****"
        if style == "tail":
            return f"{head}{mid}{self.digits(2)}****{self.digits(3)}*"
        return f"{head}{mid}{self.digits(2)}********"

    def address(self):
        city, gu, area = self.r.choice(REGIONS)
        road = self.r.choice(ROADS)
        num = self.r.randint(1, 180)
        sub = f"{self.r.randint(1, 40)}길 " if self.r.random() < 0.5 else " "
        floor = self.r.choice(["", " 1층", " 2층", " 지하1층", f" {self.r.randint(101, 320)}호"])
        dong = f" ({self.r.choice(DONGS)})" if self.r.random() < 0.4 else ""
        return f"{city} {gu} {road}{sub}{num}{floor}{dong}".replace("  ", " "), area


# --------------------------------------------------------------------------------------
# 거래 데이터 생성
# --------------------------------------------------------------------------------------
TEMPLATE_ITEMS = {"T01": "mall_snack", "T02": "cafe", "T03": "cafe", "T04": "eel", "T05": "cafe",
                  "T06": "mart", "T07": "cvs", "T08": "korean", "T09": "mart", "T10": "cafe"}
DISCOUNT_OK = {"T01", "T02", "T03", "T06", "T08", "T09", "T10"}


def make_transaction(tid, n_items, rng):
    fk = Fake(rng)
    cat = TEMPLATE_ITEMS[tid]
    pool = ITEMS[cat]
    items = []
    long_pool = ITEMS[cat.split("_")[0] + "_long"] if cat + "_long" not in ITEMS else ITEMS[cat + "_long"]
    use_long = rng.random() < 0.35
    chosen = rng.sample(pool, min(n_items, len(pool)))
    while len(chosen) < n_items:                   # 풀이 작으면 같은 상품을 다른 행으로(사이즈 변형) 추가
        p = rng.choice(pool)
        chosen.append((p[0] + rng.choice([" (2)", " 추가", " 리필"]),) + tuple(p[1:]))
    for k, p in enumerate(chosen):
        if len(items) >= n_items:          # 옵션행 포함 품목 행 수를 n_items(1~15)로 맞춘다
            break
        name, base = p[0], p[1]
        taxfree = p[2] if len(p) > 2 else False
        if use_long and k == 0:
            name = rng.choice(long_pool)
        unit = max(500, int(round(base * rng.uniform(0.85, 1.2) / (10 if cat in ("mart", "cvs") else 100))) *
                   (10 if cat in ("mart", "cvs") else 100))
        if cat == "mart":
            unit = unit // 100 * 100 + rng.choice([0, 80, 80, 80, 50])
        qty = rng.choices([1, 2, 3, 4, 5], weights=[70, 15, 7, 5, 3])[0]
        it = {"name": name, "quantity": qty, "unit_price": unit, "amount": qty * unit, "taxfree": taxfree,
              "disc": 0}
        items.append(it)
        # 옵션행(0원 또는 유료 옵션): 단가·수량·금액이 인쇄되므로 정답 items에 포함
        if tid in ("T02", "T10") and len(items) < n_items and rng.random() < 0.4:
            oname, oprice = rng.choice([("매장용컵", 0), ("테이크아웃컵", 0), ("ICE", 0), ("샷추가", 500),
                                        ("시럽추가", 300)])
            items.append({"name": oname, "quantity": qty, "unit_price": oprice, "amount": qty * oprice,
                          "taxfree": False, "disc": 0, "option": True})
    # 할인
    discount = None
    if tid in ("T06", "T09"):
        discount = 0
        for it in items:
            if not it["taxfree"] and rng.random() < 0.35:
                it["disc"] = int(it["amount"] * rng.choice([0.1, 0.2, 0.3]) // 10 * 10)
                discount += it["disc"]
    elif tid in DISCOUNT_OK and rng.random() < 0.25:
        sub = sum(i["amount"] for i in items)
        discount = min(sub - 100, rng.choice([500, 1000, 1500, 2000, int(sub * 0.1) // 100 * 100]))
        discount = max(discount, 100)
    subtotal = sum(i["amount"] for i in items)
    total = subtotal - (discount or 0)
    exempt = sum(i["amount"] for i in items if i["taxfree"])
    taxable = total - exempt
    vat = taxable // 11          # 부가세 포함가 기준: VAT = floor(과세대상액/11)
    supply = taxable - vat

    start = datetime(2025, 1, 1)
    dt = start + timedelta(days=rng.randint(0, 663), seconds=rng.randint(7 * 3600, 23 * 3600 - 1))
    method = rng.choices(["신용카드", "체크카드", "현금", "간편결제"], weights=[50, 25, 13, 12])[0]
    if tid == "T03":   # 키오스크는 현금 불가
        method = rng.choice(["신용카드", "체크카드", "간편결제"])
    addr, area = fk.address()
    tx = dict(tid=tid, items=items, subtotal=subtotal, discount=discount, total=total, exempt=exempt,
              taxable=taxable, vat=vat, supply=supply, dt=dt, method=method,
              brand=rng.choice(BRANDS[tid]), branch=rng.choice(BRANCHES), owner=fk.person(),
              biz=fk.biz_no(), phone=fk.phone(area), addr=addr,
              card_co=rng.choice(CARD_COS), pay_app=rng.choice(PAY_APPS),
              approval=fk.digits(8), merchant_no="00" + fk.digits(8), fk=fk)
    return tx


# --------------------------------------------------------------------------------------
# 템플릿 렌더링 (각 함수는 렌더러에 그리고, 이미지에 실제로 표시된 정답 값을 dict로 돌려준다)
# --------------------------------------------------------------------------------------
def card_lines(r, tx, style):
    """결제정보 블록. 현금/간편결제/카드에 따라 다르게."""
    fk = tx["fk"]
    m = tx["method"]
    if m == "현금":
        r.line([("현금", "l", X0), (won(tx["total"]), "r", X1)])
        r.line([("현금영수증", "l", X0), ("미발행", "r", X1)])
        return
    if m == "간편결제":
        r.line([("간편결제", "l", X0), (won(tx["total"]), "r", X1)])
        r.line([("결제사", "l", X0), (tx["pay_app"], "r", X1)])
        r.line([("승인번호", "l", X0), (tx["approval"], "r", X1)])
        return
    co = tx["card_co"] if m == "신용카드" else tx["card_co"].replace("카드", "체크")
    r.line([(co, "l", X0), (fk.card_masked(style), "r", X1)])
    r.line([("승인번호", "l", X0), (tx["approval"], "r", X1)])


def item_rows(r, tx, cols, name_w, size=21, name_line_first=False, opt_prefix="", show_unit=True,
              mark_flags=False):
    """품목 행. cols = (unit_x, qty_x, amt_x) 우측 정렬 기준.
    name_line_first=True면 품목명 줄 다음 줄에 숫자를 찍는다(T04/T05/T08 형식)."""
    ux, qx, ax = cols
    for it in tx["items"]:
        nm = (opt_prefix + it["name"]) if it.get("option") else it["name"]
        amt = won(it["amount"])
        if mark_flags:
            amt += ("*" if it["disc"] else "") + ("#" if it["taxfree"] else "")
        nums = []
        if show_unit:
            nums.append((won(it["unit_price"]), "r", ux))
        nums += [(str(it["quantity"]), "r", qx), (amt, "r", ax)]
        if name_line_first:
            for w_ in r.wrap(nm, X1 - X0, size):
                r.line([(w_, "l", X0)], size=size, gap=1.05)
            r.line(nums, size=size)
        else:
            wl = r.wrap(nm, name_w, size)
            for w_ in wl[:-1]:
                r.line([(w_, "l", X0 + 8)], size=size, gap=1.05)
            r.line([(wl[-1], "l", X0 + 8)] + nums, size=size)


def tpl_T01(r, tx):
    fk = tx["fk"]
    r.space(6)
    r.text("Lunafield", "c", size=64, kind="script", gap=1.25, record=True)
    r.line([(f"사업자번호: {tx['biz']}", "l", X0)])
    r.line([(f"대표: {tx['owner']}", "l", X0)])
    for i, a in enumerate(r.wrap(f"주소: {tx['addr']}", X1 - X0)):
        r.line([(a, "l", X0)])
    merchant = tx["brand"]
    r.line([(f"매장: {merchant}", "l", X0)], key="merchant")
    r.line([(f"TEL. {tx['phone']}", "l", X0)])
    r.space(14)
    for s in ["개정된 여신 전문 금융업 법에 따라", "환불/교환/재결제를 원하는 경우는", "결제하셨던 영수증 및 카드를 반드시",
              "지참하셔야 합니다.(현금/상품권은", "영수증만 지참, 타점 환불 불가)"]:
        r.line([(s, "l", X0 + 10)], size=19)
    r.space(10)
    dts = tx["dt"].strftime("%Y-%m-%d %H:%M")
    r.line([(f"구매 {dts} 거래번호:{fk.digits(4)}-{fk.digits(4)}", "l", X0)], underline=True, key="datetime")
    r.line([("상품명", "l", X0 + 40), ("수량", "r", 420), ("금액", "r", X1)])
    item_rows(r, tx, (0, 420, X1), 300, show_unit=False)
    r.space(10)
    r.line([("소      계", "l", X0 + 120), (won(tx["subtotal"]), "r", X1)], bold=True)
    if tx["discount"]:
        r.line([("쿠 폰 할 인", "l", X0), ("-" + won(tx["discount"]), "r", X1)])
    r.line([("과 세 물 품 가액", "l", X0), (won(tx["supply"]), "r", X1)])
    r.line([("부    가    세", "l", X0), (won(tx["vat"]), "r", X1)])
    r.sep()
    r.line([("합      계", "l", X0), (won(tx["total"]), "r", X1)], size=30, bold=True, key="total")
    r.sep()
    r.line([(f"{tx['method']} 결제액", "l", X0), (won(tx["total"]), "r", X1)])
    card_lines(r, tx, "tail")
    r.sep()
    r.space(8)
    r.text(f"캐셔No:{fk.digits(6)} {merchant}", "c")
    r.barcode(fk.digits(4) + tx["dt"].strftime("%Y%m%d") + fk.digits(6))
    r.space(8)
    r.text("주차정산QR코드", "c", size=26, kind="sans")
    r.qr()
    return dict(merchant_name=merchant, dt_fmt=dts, has_seconds=False, unit_shown=False,
                subtotal=tx["subtotal"], discount=tx["discount"] or None)


def tpl_T02(r, tx):
    fk = tx["fk"]
    r.space(10)
    r.text(f"[진동벨 번호 : {tx['fk'].r.randint(1, 99)}]", "c", size=28, bold=True, gap=1.6)
    merchant = f"{tx['brand']} {tx['branch']}점"
    r.line([(merchant, "l", X0)], key="merchant")
    r.line([(f"사업자 번호 : {tx['biz']}", "l", X0)])
    r.line([(f"전화번호 : {tx['phone']}", "l", X0)])
    for a in r.wrap(tx["addr"], X1 - X0):
        r.line([(a, "l", X0)])
    dts = tx["dt"].strftime("%Y-%m-%d %H:%M:%S")
    r.line([(f"정상 {dts}", "l", X0)], key="datetime")
    r.line([(f"거래번호:{fk.digits(4)}-{fk.digits(4)}", "l", X0)])
    r.sep()
    r.line([("상품명", "l", X0), ("단가", "r", 400), ("수량", "r", 460), ("금액", "r", X1)])
    r.sep()
    item_rows(r, tx, (400, 460, X1), 220, opt_prefix="ㄴ ")
    r.sep()
    r.line([("과세물품가액", "l", X0), (won(tx["supply"]), "r", X1)])
    r.line([("부    가    세", "l", X0), (won(tx["vat"]), "r", X1)])
    r.sep()
    r.line([("합      계", "l", X0), (won(tx["subtotal"]), "r", X1)])
    if tx["discount"]:
        r.line([("할      인", "l", X0), ("-" + won(tx["discount"]), "r", X1)])
    r.line([("결제금액", "l", X0), (won(tx["total"]), "r", X1)], size=32, bold=True, key="total")
    if tx["method"] in ("신용카드", "체크카드"):
        r.line([(tx["method"], "l", X0), (won(tx["total"]), "r", X1)])
    card_lines(r, tx, "tail")
    r.sep()
    r.text(f"NO:{fk.digits(2)}***{fk.digits(3)}* 담당자:{fk.digits(1)}**{fk.digits(1)}", "c")
    r.barcode(tx["dt"].strftime("%Y%m%d") + fk.digits(12))
    r.space(10)
    r.text(f"오늘도 {tx['brand'].replace('(주)', '')}를 찾아주셔서", "c")
    r.text("감사합니다.", "c")
    return dict(merchant_name=merchant, dt_fmt=dts, has_seconds=True, unit_shown=True,
                subtotal=tx["subtotal"], discount=tx["discount"] or None)


def tpl_T03(r, tx):
    fk = tx["fk"]
    r.text("천장 모니터의 주문번호를 확인해 주세요", "c", size=22, kind="sans", gap=1.6)
    r.text(f"주문번호: {fk.r.randint(1, 499)}", "c", size=48, kind="sans", bold=True, gap=1.5)
    r.line([(f"* 와이파이 ID: {tx['brand']}_Guest", "l", X0)])
    r.line([(f"* 와이파이 PW: {fk.digits(8)}", "l", X0)])
    r.sep()
    merchant = f"{tx['brand']}({tx['branch']}점)"
    r.line([(merchant, "l", X0)], key="merchant")
    r.line([(f"(대표: {tx['owner']})", "l", X0)])
    r.line([(f"사업자번호: {tx['biz']}", "l", X0)])
    for a in r.wrap(tx["addr"], X1 - X0):
        r.line([(a, "l", X0)])
    r.line([(f"(T:{tx['phone']})", "l", X0)])
    r.sep()
    r.line([(f"기기명: {tx['branch']}점_KIOSK{fk.digits(2)}", "l", X0)])
    d = tx["dt"]
    dts = f"{d.year}년{d.month:02d}월{d.day:02d}일{d.hour:02d}시{d.minute:02d}분{d.second:02d}초"
    r.line([(f"거래일시: {dts}", "l", X0)], key="datetime")
    r.line([(f"TID: {fk.digits(10)}", "l", X0)])
    if tx["method"] == "간편결제":
        r.line([(f"결제사: {tx['pay_app']}", "l", X0)])
        r.line([(f"승인번호: {tx['approval']}", "l", X0)])
    else:
        r.line([(f"승인번호: {tx['approval']}", "l", X0)])
        r.line([(f"가맹점번호: {tx['merchant_no']}", "l", X0)])
        co = tx["card_co"] if tx["method"] == "신용카드" else tx["card_co"].replace("카드", "체크")
        r.line([(f"카드종류: {co}", "l", X0)])
        r.line([(f"카드번호: {fk.card_masked('dash')}", "l", X0)])
    r.sep()
    r.text("주문내역", "l", size=28, kind="sans", bold=True)
    for it in tx["items"]:
        wl = r.wrap(f"{it['name']}({it['quantity']}개)", 380)
        for w_ in wl[:-1]:
            r.line([(w_, "l", X0)])
        r.line([(wl[-1], "l", X0), (won(it["amount"]) + "원", "r", X1)])
    r.sep()
    r.line([("합계", "l", X0), (won(tx["subtotal"]) + "원", "r", X1)], size=28, kind="sans", bold=True)
    if tx["discount"]:
        r.line([("할인", "l", X0), ("-" + won(tx["discount"]) + "원", "r", X1)])
    r.line([("결제금액", "l", X0), (won(tx["total"]) + "원", "r", X1)], size=28, kind="sans", bold=True,
           key="total")
    r.line([(f"{tx['method']} 결제", "l", X0), (won(tx["total"]) + "원", "r", X1)])
    r.line([(f"(부가세{won(tx['vat'])}원포함)", "r", X1)])
    r.sep()
    r.line([(f"{tx['brand']} 고객센터 1500-0000", "l", X0)])
    r.line([("감사합니다.", "l", X0)])
    return dict(merchant_name=merchant, dt_fmt=dts, has_seconds=True, unit_shown=False,
                subtotal=tx["subtotal"], discount=tx["discount"] or None)


def tpl_T04(r, tx):
    fk = tx["fk"]
    r.space(10)
    r.text("[ 카드판매 영수증 ]" if tx["method"] != "현금" else "[ 현금판매 영수증 ]", "c", size=28, bold=True)
    r.text("[고객용]", "c")
    r.space(12)
    merchant = tx["brand"]
    r.text(merchant, "c", size=24, key="merchant")
    r.line([(f"사업자번호 : {tx['biz']}", "l", X0)])
    r.line([(f"대표자: {tx['owner']}", "l", X0), (f"TEL: {tx['phone']}", "l", 300)])
    for a in r.wrap(tx["addr"], X1 - X0):
        r.line([(a, "l", X0)])
    r.sep()
    r.text(f"테이블명: {fk.r.randint(1, 30)}", "c", size=34, bold=True)
    d = tx["dt"]
    dts = d.strftime("%Y%m%d %H:%M:%S")
    r.line([(f"판매시간: {dts}", "l", X0), ("(POS100)", "r", X1)], key="datetime")
    r.line([("판매사원:", "l", X0), (f"영수번호:: {fk.r.randint(1, 300)}", "l", 260)])
    r.sep("=")
    r.line([("상품", "l", X0), ("단가", "r", 360), ("수량", "r", 440), ("금액", "r", X1)])
    r.sep("=")
    item_rows(r, tx, (360, 440, X1), 0, name_line_first=True)
    r.sep()
    r.line([("합      계::", "l", X0), (won(tx["total"]), "r", X1)], size=30, bold=True, key="total")
    r.line([(f"{tx['method']}::", "l", X0), (won(tx["total"]), "r", X1)])
    r.sep()
    r.line([("공 급 가::", "l", X0), (won(tx["supply"]), "r", X1)])
    r.line([("부 가 세::", "l", X0), (won(tx["vat"]), "r", X1)])
    r.sep()
    if tx["method"] in ("신용카드", "체크카드"):
        co = tx["card_co"] if tx["method"] == "신용카드" else tx["card_co"].replace("카드", "체크")
        r.line([(f"[카드종류] {co}", "l", X0), ("[할부개월] 일시불", "r", X1)])
        r.line([("[카 드 번 호]", "l", X0), (fk.card_masked("dash"), "l", 250)])
        ap = d - timedelta(seconds=fk.r.randint(0, 40))
        r.line([("[승 인 일 시]", "l", X0), (ap.strftime("%Y-%m-%d %H:%M:%S"), "l", 250)])
        r.line([("[승 인 번 호]", "l", X0), (tx["approval"], "l", 250)])
        r.line([("[카드매출]", "l", X0), (won(tx["total"]), "r", 480)], size=28, bold=True)
        r.line([("- 공 급 가", "l", X0 + 50), (won(tx["supply"]), "r", 480)])
        r.line([("- 부 가 세", "l", X0 + 50), (won(tx["vat"]), "r", 480)])
        r.sep()
        r.text("신용 카드 전표", "c")
    else:
        card_lines(r, tx, "dash")
    r.sep()
    r.text(d.strftime("%Y%m%d") + fk.digits(8), "c")
    return dict(merchant_name=merchant, dt_fmt=dts, has_seconds=True, unit_shown=True,
                subtotal=None, discount=None)


def tpl_T05(r, tx):
    fk = tx["fk"]
    merchant = f"{tx['brand']} {tx['branch']}센트럴"
    r.space(16)
    r.line([(merchant, "l", X0)], size=22, kind="sans", gap=1.0, key="merchant")
    r.line([(f"주문번호 : 키오-{fk.digits(3)}", "l", X0)], size=40, kind="sans", bold=True)
    r.sep()
    d = tx["dt"]
    dts = d.strftime("%Y-%m-%d %H:%M:%S")
    if tx["method"] in ("신용카드", "체크카드"):
        co = tx["card_co"] if tx["method"] == "신용카드" else tx["card_co"].replace("카드", "체크")
        r.line([("결제방법", "l", X0), (f"{tx['method']}({co})", "r", X1)], kind="sans")
        r.line([("카드번호", "l", X0), (fk.card_masked("star"), "r", X1)], kind="sans")
        r.line([("할부기간", "l", X0), ("일시불", "r", X1)], kind="sans")
    else:
        r.line([("결제방법", "l", X0), (tx["method"] + (f"({tx['pay_app']})" if tx["method"] == "간편결제" else ""),
                                        "r", X1)], kind="sans")
    r.line([("부가세액", "l", X0), (won(tx["vat"]), "r", X1)], kind="sans")
    r.line([("공급가액", "l", X0), (won(tx["supply"]), "r", X1)], kind="sans")
    r.line([("승인번호", "l", X0), (tx["approval"], "r", X1)], kind="sans")
    r.line([("승인일시", "l", X0), (dts, "r", X1)], kind="sans", key="datetime")
    r.sep()
    r.line([("메뉴명", "l", X0), ("단가", "r", 330), ("수량", "r", 430), ("금액", "r", X1)], kind="sans")
    r.sep()
    opts = ["(진한,다크초콜릿)", "(연하게,얼음적게)", "(샷추가없음)", "(오트밀크)", "(시럽없음)"]
    for it in tx["items"]:
        for w_ in r.wrap(it["name"], X1 - X0, kind="sans"):
            r.line([(w_, "l", X0)], kind="sans", gap=1.05)
        r.line([(won(it["unit_price"]), "r", 330), (str(it["quantity"]), "r", 430),
                (won(it["amount"]), "r", X1)], kind="sans")
        if fk.r.random() < 0.3:   # 가격 없는 옵션 설명 줄(정답 items에는 넣지 않음)
            r.line([(f"{tx['branch']}{fk.r.choice(opts)}", "l", X0)], kind="sans")
    r.sep()
    r.line([("합계", "l", X0), (won(tx["total"]), "r", X1)], size=24, kind="sans", key="total")
    r.sep()
    r.line([("상호", "l", X0), (merchant, "r", X1)], kind="sans")
    r.line([("대표", "l", X0), (tx["owner"], "r", X1)], kind="sans")
    r.line([("사업자등록번호", "l", X0), (tx["biz"], "r", X1)], kind="sans")
    r.line([("대표번호", "l", X0), (tx["phone"], "r", X1)], kind="sans")
    al = r.wrap(tx["addr"], 400, kind="sans")
    r.line([("주소", "l", X0), (al[0], "r", X1)], kind="sans")
    for a in al[1:]:
        r.line([(a, "r", X1)], kind="sans")
    r.sep()
    return dict(merchant_name=merchant, dt_fmt=dts, has_seconds=True, unit_shown=True,
                subtotal=None, discount=None)


def tpl_mart(r, tx, variant):
    fk = tx["fk"]
    corp = fk.r.choice(["(주)행복유통", "(주)알뜰상사", "(주)이웃리테일", "(주)온가족유통"])
    store = f"{tx['brand']} {tx['branch']}점"
    merchant = f"{corp}({store})"
    for i, s in enumerate(r.wrap(merchant, X1 - X0, size=26, kind="sans")):
        r.line([(s, "l", X0)], size=26, kind="sans", gap=1.05, key="merchant")
    r.line([(f"사업자번호:{tx['biz']}", "l", X0)])
    r.line([(f"대 표 자:{tx['owner']}", "l", X0)])
    for a in r.wrap(f"주    소:{tx['addr']}", X1 - X0):
        r.line([(a, "l", X0)])
    r.line([(f"전화번호:{tx['phone']}", "l", X0)])
    r.space(8)
    d = tx["dt"]
    dts = d.strftime("%y-%m-%d %H:%M") + f",{WEEK[d.weekday()]}요일"
    r.line([(f"판매일:{dts} 계산대:{fk.digits(3)}", "l", X0)], key="datetime")
    r.sep()
    r.line([("상품명", "l", X0), ("단가", "r", 350), ("수량", "r", 430), ("금액", "r", X1 - 18)])
    r.sep()
    item_rows(r, tx, (350, 430, X1 - 18), 200, mark_flags=True)
    r.sep()
    r.line([("(*)할인금액 :", "l", X0), ("-" + won(tx["discount"]) if tx["discount"] else "0", "r", X1)])
    r.line([("(#)면세물품 :", "l", X0), (won(tx["exempt"]), "r", X1)])
    r.line([("과 세 물 품 :", "l", X0), (won(tx["supply"]), "r", X1)])
    r.line([("부가세(VAT) :", "l", X0), (won(tx["vat"]), "r", X1)])
    r.line([("합      계:", "l", X0), (won(tx["total"]), "r", X1)], size=30, bold=True, key="total")
    r.line([(f"{tx['method']}지불 :", "l", X0), (won(tx["total"]), "r", X1)])
    r.sep()
    if tx["method"] in ("신용카드", "체크카드"):
        r.text("*카드신용승인*")
        for s in r.wrap(f"가맹점명 : {merchant}", X1 - X0):
            r.line([(s, "l", X0)])
        r.line([(f"카드번호 : {fk.card_masked('dash')}", "l", X0)])
        co = tx["card_co"] if tx["method"] == "신용카드" else tx["card_co"].replace("카드", "체크")
        r.line([(f"카 드 명 : {co}", "l", X0)])
        r.space(8)
        r.line([(f"승인금액 : {won(tx['total'])}", "l", X0), ("(일시불)", "l", 330)])
        r.line([(f"승인번호 : {tx['approval']}, 전표No:{fk.digits(6)}", "l", X0)])
        r.text("정상승인")
    else:
        card_lines(r, tx, "dash")
    if variant == "T09":
        r.sep()
        r.text("*** 포인트 적립 안내 ***", "c")
        r.line([("적립포인트", "l", X0), (won(tx["total"] // 100), "r", X1)])
        r.line([("회원번호", "l", X0), (f"{fk.digits(3)}****{fk.digits(2)}", "r", X1)])
    r.sep()
    r.line([("교환/반품시 영수증지참 7일이내가능", "l", X0)])
    r.line([("정육/수산/농산/냉장/냉동 2일이내가능", "l", X0)])
    r.line([(f"거래NO:{fk.digits(10)} 계산원:{fk.person()}({fk.digits(3)})", "l", X0)])
    r.barcode(d.strftime("%y%m%d") + fk.digits(8))
    return dict(merchant_name=merchant, dt_fmt=dts, has_seconds=False, unit_shown=True,
                subtotal=None, discount=tx["discount"])


def tpl_T07(r, tx):
    fk = tx["fk"]
    r.space(6)
    r.text(tx["brand"].replace("24", " 24").upper(), "c", size=58, kind="sans", bold=True, gap=1.15)
    r.line([("(주)이음리테일", "l", X0), ("www.example-cvs.kr", "r", X1)], size=16, kind="sans")
    merchant = f"{tx['brand']} {tx['branch']}점#{fk.digits(5)}"
    r.line([(merchant, "l", X0)], key="merchant")
    r.line([("(--)", "l", X0)])
    r.line([(tx["owner"], "l", X0), (tx["biz"].replace("-", ""), "l", 250)])
    for a in r.wrap(tx["addr"], X1 - X0):
        r.line([(a, "l", X0)])
    d = tx["dt"]
    dts = d.strftime("%Y-%m-%d") + f" ({WEEK[d.weekday()]}) " + d.strftime("%H:%M:%S")
    r.line([(f"[판  매] {dts}", "l", X0)], key="datetime")
    r.sep()
    r.line([("상 품 명", "l", X0 + 40), ("수량", "r", 420), ("금 액", "r", X1)])
    r.sep()
    for it in tx["items"]:
        for w_ in r.wrap(it["name"], X1 - X0):
            r.line([(w_, "l", X0)], gap=1.05)
        r.line([("200" + fk.digits(10), "l", X0 + 10), (str(it["quantity"]), "r", 420), (won(it["amount"]), "r", X1)])
    r.line([("과세물품가액", "l", 260), (won(tx["supply"]), "r", X1)])
    r.line([("부    가    세", "l", 260), (won(tx["vat"]), "r", X1)])
    r.line([("합계", "l", X0), ("₩" + won(tx["total"]), "r", X1)], size=30, bold=True, key="total")
    r.sep()
    r.text(f"*** {tx['method']} 승인정보[고객용] ***", "c")
    if tx["method"] in ("신용카드", "체크카드"):
        r.line([(tx["card_co"], "l", X0), (won(tx["total"]), "r", X1)])
        r.line([(fk.card_masked("star")[:8], "l", X0)])
        r.line([("승인번호", "l", X0), (tx["approval"], "l", 160), ("(N)일시불", "r", X1)])
    else:
        card_lines(r, tx, "star")
    r.sep()
    r.line([(f"P:{fk.digits(2)}-{fk.digits(2)} CNT:{fk.digits(4)}", "l", X0)])
    r.line([(f"{tx['brand']} 고객센터 1500-0000", "l", X0)])
    r.barcode(fk.digits(4) + d.strftime("%y%m%d") + fk.digits(8))
    r.sep()
    r.line([("Thank you for visiting.", "l", X0)])
    r.line([("Keep this receipt for refunds.", "l", X0)])
    r.line([("교환/환불은 영수증 및 결제카드를", "l", X0)])
    r.line([("지참하시어 30일 이내 점포 방문", "l", X0)])
    return dict(merchant_name=merchant, dt_fmt=dts, has_seconds=True, unit_shown=False,
                subtotal=None, discount=None)


def tpl_T08(r, tx):
    fk = tx["fk"]
    r.space(10)
    merchant = f"{tx['brand']}({tx['branch']})"
    r.line([(f"[매장명] {merchant}", "l", X0)], key="merchant")
    r.line([(f"[사업자] {tx['biz']}", "l", X0)])
    for a in r.wrap(f"[주  소] {tx['addr']}", X1 - X0):
        r.line([(a, "l", X0)])
    r.line([(f"[대표자] {tx['owner']}", "l", X0), (f"[TEL] {tx['phone']}", "l", 270)])
    d = tx["dt"]
    dts = d.strftime("%Y-%m-%d %H:%M:%S")
    r.line([(f"[매출일] {dts}", "l", X0)], key="datetime")
    r.line([(f"[영수증] {d.strftime('%Y%m%d')}-01-{fk.digits(4)}/ 기본-{fk.r.randint(1, 9)}", "l", X0)])
    r.sep("=")
    r.line([("상 품 명", "l", X0 + 30), ("단가", "r", 360), ("수량", "r", 440), ("금 액", "r", X1)])
    r.sep()
    for it in tx["items"]:
        for w_ in r.wrap(it["name"], X1 - X0):
            r.line([(w_, "l", X0)], gap=1.05)
        r.line([(fk.digits(6), "l", X0), (won(it["unit_price"]), "r", 360), (str(it["quantity"]), "r", 440),
                (won(it["amount"]), "r", X1)])
    r.sep()
    r.line([("합 계 금 액", "l", X0), (won(tx["subtotal"]), "r", X1)])
    if tx["discount"]:
        r.line([("할 인 금 액", "l", X0), ("-" + won(tx["discount"]), "r", X1)])
    r.sep()
    r.line([("부가세 과세물품가액", "l", X0 + 120), (won(tx["supply"]), "r", X1)])
    r.line([("부      가      세", "l", X0 + 120), (won(tx["vat"]), "r", X1)])
    r.sep()
    r.line([("받 을 금 액", "l", X0), (won(tx["total"]), "r", X1)], size=32, bold=True, key="total")
    r.line([("받 은 금 액", "l", X0), (won(tx["total"]), "r", X1)], size=32, bold=True)
    r.sep()
    if tx["method"] in ("신용카드", "체크카드"):
        r.text(f"*** {tx['method']} 승인정보(고객용)[1] ***", "c")
        co = tx["card_co"] if tx["method"] == "신용카드" else tx["card_co"].replace("카드", "체크")
        r.line([("카 드 종 류:", "l", X0), (co, "r", X1)])
        r.line([(f"카 드 번 호: {fk.card_masked('dash')}", "l", X0)])
        r.line([("할 부 개 월: 일시불", "l", X0)])
        r.line([("판 매 금 액:", "l", X0), (won(tx["supply"]), "r", X1)])
        r.line([("부 가 세 :", "l", X0), (won(tx["vat"]), "r", X1)])
        r.line([("승 인 금 액:", "l", X0), (won(tx["total"]), "r", X1)])
        r.line([(f"승 인 번 호: {tx['approval']}", "l", X0)])
        ap = d - timedelta(seconds=1)
        r.line([(f"승 인 일 시: {ap.strftime('%Y-%m-%d %H:%M:%S')}", "l", X0)])
        r.line([(f"가맹점 번호: {tx['merchant_no']}", "l", X0)])
    else:
        card_lines(r, tx, "dash")
    r.sep()
    return dict(merchant_name=merchant, dt_fmt=dts, has_seconds=True, unit_shown=True,
                subtotal=tx["subtotal"], discount=tx["discount"] or None)


def tpl_T10(r, tx):
    fk = tx["fk"]
    r.space(14)
    r.text("[영수증]", "c", size=34, kind="sans", gap=1.4)
    merchant = tx["brand"]
    r.line([(f"[매장명] {merchant}/{tx['biz']}", "l", X0)], key="merchant")
    for a in r.wrap(f"[주  소] {tx['addr']}", X1 - X0):
        r.line([(a, "l", X0)])
    r.line([(f"[대표자] {tx['owner']}", "l", X0), (f"[TEL] {tx['phone']}", "l", 270)])
    d = tx["dt"]
    dts = d.strftime("%Y-%m-%d %H:%M:%S")
    r.line([(f"[매출일] {dts}", "l", X0)], key="datetime")
    r.line([(f"[영수증] {d.strftime('%Y%m%d')}-02-{fk.digits(4)}", "l", X0)])
    r.line([("주문번호", "l", 180), (fk.digits(4), "l", 300)], size=24)
    r.y -= 8
    r.line([(fk.r.choice(["내점", "포장"]), "l", X0)])
    r.sep("=")
    r.line([("상 품 명", "l", X0 + 40), ("단 가", "r", 380), ("수량", "r", 450), ("금 액", "r", X1)])
    r.sep()
    item_rows(r, tx, (380, 450, X1), 220, opt_prefix="▶")
    r.sep()
    r.line([("합 계 금 액", "l", X0), (won(tx["subtotal"]), "r", X1)])
    if tx["discount"]:
        r.line([("할 인 금 액", "l", X0), ("-" + won(tx["discount"]), "r", X1)])
    r.sep()
    r.line([("부가세 과세물품가액", "l", X0 + 120), (won(tx["supply"]), "r", X1)])
    r.line([("부      가      세", "l", X0 + 120), (won(tx["vat"]), "r", X1)])
    r.sep()
    r.line([("받 은 금 액", "l", X0), (won(tx["total"]), "r", X1)], size=24, key="total")
    r.sep()
    r.line([(tx["method"].replace("카드", " 카드"), "l", X0), (won(tx["total"]), "r", X1)], size=24)
    r.sep()
    if tx["method"] in ("신용카드", "체크카드"):
        r.text(f"*** {tx['method']} 승인정보(고객용)[1] ***", "c")
        r.line([(f"카드/매입사: {tx['card_co']}", "l", X0)])
        r.line([(f"카 드 번 호: {fk.card_masked('dash')}", "l", X0)])
        r.line([(f"승 인 금 액: {won(tx['total'])}(일시불)", "l", X0)])
        r.line([(f"승인/가맹점: {tx['approval']} / {tx['merchant_no']}", "l", X0)])
        ap = d + timedelta(seconds=1)
        r.line([(f"승 인 일 시: {ap.strftime('%Y-%m-%d %H:%M:%S')}", "l", X0)])
        r.line([(f"단말기 번호: {fk.digits(8)}", "l", X0)])
        r.line([("NOTICE: 무서명거래", "l", X0 + 80)])
    else:
        card_lines(r, tx, "dash")
    r.sep()
    r.text("*** 회원 마일리지 정보[1] ***", "c")
    r.line([("회원번호", "l", X0), (f"{fk.digits(3)}****{fk.digits(1)} 님", "r", X1)])
    pts = tx["total"] // 50
    r.line([("적립", "l", X0), (won(pts), "r", 280), ("사용", "l", 330), ("0", "r", X1)])
    r.sep()
    return dict(merchant_name=merchant, dt_fmt=dts, has_seconds=True, unit_shown=True,
                subtotal=tx["subtotal"], discount=tx["discount"] or None)


TEMPLATES = {"T01": tpl_T01, "T02": tpl_T02, "T03": tpl_T03, "T04": tpl_T04, "T05": tpl_T05,
             "T06": lambda r, tx: tpl_mart(r, tx, "T06"), "T07": tpl_T07, "T08": tpl_T08,
             "T09": lambda r, tx: tpl_mart(r, tx, "T09"), "T10": tpl_T10}


# --------------------------------------------------------------------------------------
# 촬영 효과 (난이도)
# --------------------------------------------------------------------------------------
def background(h, w, rng, kind):
    base = rng.randint(226, 244)
    bg = np.full((h, w, 3), base, np.float32)
    bg[..., 2] += rng.uniform(-4, 4)
    bg[..., 0] += rng.uniform(-4, 4)
    if kind == "notebook":
        step = rng.randint(34, 46)
        off = rng.randint(0, step)
        for y in range(off, h, step):
            bg[y:y + 2] -= rng.uniform(22, 35)
    elif kind == "desk":
        g = cv2.GaussianBlur(np.random.default_rng(rng.randint(0, 1 << 30)).normal(0, 8, (h, w)).astype(np.float32),
                             (0, 0), 25)
        bg += g[..., None] * 3
        bg -= rng.uniform(15, 45)
    return np.clip(bg, 0, 255)


def paper_effects(img, keys, rng, diff):
    """영수증 종이 자체의 효과(용지 색, 인쇄 흐림 띠, 구김 음영)."""
    a = np.asarray(img).astype(np.float32)
    h, w = a.shape[:2]
    nrng = np.random.default_rng(rng.randint(0, 1 << 30))
    if diff in ("medium", "hard"):
        tint = np.array([rng.uniform(-10, 2), rng.uniform(-8, 2), rng.uniform(-16, 0)], np.float32)
        a += tint
    if diff == "hard":
        protected = [b for v in keys.values() for b in v]
        # 일부 인쇄 흐림: 핵심 줄(상호·일시·합계)과 겹치지 않는 띠만 흐리게
        for _ in range(rng.randint(2, 4)):
            for _try in range(30):
                y0 = rng.randint(40, h - 80)
                bh = rng.randint(30, 90)
                if all(y0 + bh < b[1] - 8 or y0 > b[3] + 8 for b in protected):
                    break
            else:
                continue
            band = a[y0:y0 + bh]
            paper = np.percentile(band, 95, axis=(0, 1))
            alpha = rng.uniform(0.35, 0.6)
            band[:] = paper + (band - paper) * alpha
            speck = nrng.random(band.shape[:2]) < 0.04
            band[speck] = paper
        # 구김: 저주파 변위 + 음영
        amp = rng.uniform(1.5, 3.0)
        dx = cv2.GaussianBlur(nrng.normal(0, 1, (h, w)).astype(np.float32), (0, 0), 30)
        dy = cv2.GaussianBlur(nrng.normal(0, 1, (h, w)).astype(np.float32), (0, 0), 30)
        dx *= amp / (np.abs(dx).max() + 1e-6)
        dy *= amp / (np.abs(dy).max() + 1e-6)
        gx, gy = np.meshgrid(np.arange(w, dtype=np.float32), np.arange(h, dtype=np.float32))
        a = cv2.remap(a, gx + dx, gy + dy, cv2.INTER_LINEAR, borderMode=cv2.BORDER_REPLICATE)
        shade = cv2.GaussianBlur(nrng.normal(0, 1, (h, w)).astype(np.float32), (0, 0), 18)
        shade = shade / (np.abs(shade).max() + 1e-6) * rng.uniform(8, 16)
        a += shade[..., None]
        for _ in range(rng.randint(2, 5)):   # 접힌 자국
            y = rng.randint(0, h - 1)
            ang = rng.uniform(-0.15, 0.15)
            for x in range(w):
                yy = int(y + x * ang)
                if 0 <= yy < h - 2:
                    a[yy, x] -= 12
                    a[yy + 1, x] += 6
    return np.clip(a, 0, 255)


def photo(rec_arr, keys, rng, diff):
    h, w = rec_arr.shape[:2]
    corners = np.float32([[0, 0], [w, 0], [w, h], [0, h]])
    if diff == "easy":
        dst = corners.copy()
    elif diff == "medium":
        ang = math.radians(rng.uniform(-3.5, 3.5))
        c, s = math.cos(ang), math.sin(ang)
        cx, cy = w / 2, h / 2
        dst = np.float32([[cx + (x - cx) * c - (y - cy) * s, cy + (x - cx) * s + (y - cy) * c] for x, y in corners])
    else:
        j = 0.06 * w
        dst = corners + np.float32([[rng.uniform(-j, j), rng.uniform(-j, j)] for _ in range(4)])
        ang = math.radians(rng.uniform(-3, 3))
        c, s = math.cos(ang), math.sin(ang)
        cx, cy = w / 2, h / 2
        dst = np.float32([[cx + (x - cx) * c - (y - cy) * s, cy + (x - cx) * s + (y - cy) * c] for x, y in dst])
    pad = rng.randint(50, 110)
    mn = dst.min(0)
    dst = dst - mn + pad
    cw, ch = int(dst[:, 0].max() + pad), int(dst[:, 1].max() + pad)
    M = cv2.getPerspectiveTransform(corners, dst)
    bgkind = "plain" if diff == "easy" else rng.choice(["notebook", "notebook", "desk", "plain"])
    bg = background(ch, cw, rng, bgkind)
    warped = cv2.warpPerspective(rec_arr, M, (cw, ch), flags=cv2.INTER_LINEAR)
    mask = cv2.warpPerspective(np.ones((h, w), np.float32), M, (cw, ch), flags=cv2.INTER_LINEAR)[..., None]
    out = warped * mask + bg * (1 - mask)
    if diff != "easy":   # 센서 노이즈 → 밝기 → 흐림 순서(흐림이 노이즈를 눌러 PNG 용량도 줄어듦)
        out += np.random.default_rng(rng.randint(0, 1 << 30)).normal(0, rng.uniform(2.0, 4.0), out.shape)
        out *= rng.uniform(0.82, 1.08)
        out = cv2.GaussianBlur(out, (0, 0), rng.uniform(0.5, 0.95) if diff == "medium" else rng.uniform(0.8, 1.2))
    if diff == "hard":   # 그림자: 한쪽에서 들어오는 부드러운 어둠
        gx, gy = np.meshgrid(np.linspace(0, 1, cw), np.linspace(0, 1, ch))
        th = rng.uniform(0, 2 * math.pi)
        t = (gx - 0.5) * math.cos(th) + (gy - 0.5) * math.sin(th)
        cut = rng.uniform(-0.15, 0.25)
        sh = 1 - rng.uniform(0.25, 0.4) * (1 / (1 + np.exp(-(t - cut) * 18)))
        out *= sh[..., None]
    # 핵심 박스를 변환해서 화면 안에 있는지 반환
    tk = {}
    for k, boxes in keys.items():
        tb = []
        for (x0, y0, x1, y1) in boxes:
            pts = cv2.perspectiveTransform(np.float32([[[x0, y0], [x1, y0], [x1, y1], [x0, y1]]]), M)[0]
            tb.append([float(pts[:, 0].min()), float(pts[:, 1].min()), float(pts[:, 0].max()), float(pts[:, 1].max())])
        tk[k] = tb
    return np.clip(out, 0, 255).astype(np.uint8), tk, (cw, ch)


# --------------------------------------------------------------------------------------
# 메인
# --------------------------------------------------------------------------------------
def build_one(idx, tid, diff, n_items, rng):
    tx = make_transaction(tid, n_items, rng)
    ink_v = rng.randint(20, 60)
    ink = (ink_v, ink_v, ink_v + rng.randint(0, 25)) if rng.random() < 0.3 else (ink_v, ink_v, ink_v)
    r = Renderer(paper=(rng.randint(248, 255),) * 3, ink=ink, rng=rng)
    r.mark()
    meta = TEMPLATES[tid](r, tx)
    img, lines, keys = r.finish()
    arr = paper_effects(img, keys, rng, diff)
    out, tkeys, size = photo(arr, keys, rng, diff)
    fmt = "%Y-%m-%dT%H:%M:%S" if meta["has_seconds"] else "%Y-%m-%dT%H:%M"
    items = [{"name": it["name"], "quantity": it["quantity"],
              "unit_price": it["unit_price"] if meta["unit_shown"] else None, "amount": it["amount"]}
             for it in tx["items"]]
    label = {
        "id": f"receipt_{idx:04d}",
        "source_template_id": tid,
        "is_synthetic": True,
        "difficulty": diff,
        "merchant_name": meta["merchant_name"],
        "transaction_datetime": tx["dt"].strftime(fmt),
        "items": items,
        "subtotal": meta["subtotal"],
        "discount": meta["discount"],
        "total": tx["total"],
        "payment_method": tx["method"],
        "full_text": "\n".join(lines),
    }
    aux = {"keys": tkeys, "size": size, "dt_fmt": meta["dt_fmt"], "vat": tx["vat"], "supply": tx["supply"],
           "taxable": tx["taxable"], "item_disc": [it["disc"] for it in tx["items"]],
           "raw_items": tx["items"]}
    return Image.fromarray(out), label, aux


def validate(out_dir, labels, auxes):
    rep = {"performed": {}, "issues": []}
    imgs = sorted(os.listdir(os.path.join(out_dir, "images")))
    js = sorted(os.listdir(os.path.join(out_dir, "labels")))
    rep["performed"]["image_count"] = len(imgs)
    rep["performed"]["label_count"] = len(js)
    stems_i = {os.path.splitext(f)[0] for f in imgs}
    stems_j = {os.path.splitext(f)[0] for f in js}
    rep["performed"]["one_to_one"] = stems_i == stems_j and len(imgs) == len(js)
    # 계산 검증
    calc_err = 0
    for lb, ax in zip(labels, auxes):
        s = 0
        for it, raw in zip(lb["items"], ax["raw_items"]):
            if raw["quantity"] * raw["unit_price"] != it["amount"]:
                calc_err += 1
                rep["issues"].append(f"{lb['id']} 품목 금액 불일치 {it}")
            if it["unit_price"] is not None and it["unit_price"] * it["quantity"] != it["amount"]:
                calc_err += 1
            s += it["amount"]
        if lb["subtotal"] is not None and lb["subtotal"] != s:
            calc_err += 1
            rep["issues"].append(f"{lb['id']} 소계 불일치")
        if s - (lb["discount"] or 0) != lb["total"]:
            calc_err += 1
            rep["issues"].append(f"{lb['id']} 합계 불일치")
        if lb["discount"] is not None and lb["source_template_id"] in ("T06", "T09") \
                and sum(ax["item_disc"]) != lb["discount"]:
            calc_err += 1
        if ax["vat"] != ax["taxable"] // 11 or ax["vat"] + ax["supply"] != ax["taxable"]:
            calc_err += 1
            rep["issues"].append(f"{lb['id']} 부가세 계산 불일치")
        if lb["total"] <= 0:
            calc_err += 1
    rep["performed"]["calc_errors"] = calc_err
    # 정답 값이 실제 렌더링 텍스트에 있는지
    miss = 0
    for lb, ax in zip(labels, auxes):
        ft = lb["full_text"]
        flat = ft.replace("\n", "")
        if lb["merchant_name"].replace(" ", "") not in flat.replace(" ", ""):
            miss += 1
            rep["issues"].append(f"{lb['id']} 상호명이 full_text에 없음")
        if ax["dt_fmt"] not in ft:
            miss += 1
            rep["issues"].append(f"{lb['id']} 거래일시 문자열이 full_text에 없음")
        if f"{lb['total']:,}" not in ft:
            miss += 1
            rep["issues"].append(f"{lb['id']} 합계 금액이 full_text에 없음")
        if lb["payment_method"].replace("카드", "") not in ft.replace(" ", ""):
            miss += 1
            rep["issues"].append(f"{lb['id']} 결제수단이 full_text에 없음")
        if SYN_MARK not in ft:
            miss += 1
        for it in lb["items"]:
            if it["name"].replace(" ", "") not in flat.replace(" ", ""):
                miss += 1
                rep["issues"].append(f"{lb['id']} 품목명 '{it['name']}' 이 full_text에 없음")
    rep["performed"]["label_vs_rendered_text_missing"] = miss
    # 핵심 영역이 이미지 안에 있는지
    out_of_frame = 0
    for lb, ax in zip(labels, auxes):
        cw, ch = ax["size"]
        for k, boxes in ax["keys"].items():
            for b in boxes:
                if b[0] < 2 or b[1] < 2 or b[2] > cw - 2 or b[3] > ch - 2:
                    out_of_frame += 1
                    rep["issues"].append(f"{lb['id']} {k} 영역이 이미지 밖")
        for k in ("merchant", "datetime", "total"):
            if k not in ax["keys"]:
                out_of_frame += 1
                rep["issues"].append(f"{lb['id']} {k} 박스 없음")
    rep["performed"]["key_fields_out_of_frame"] = out_of_frame
    # 중복
    hashes = {}
    for f in imgs:
        with open(os.path.join(out_dir, "images", f), "rb") as fh:
            hashes.setdefault(hashlib.md5(fh.read()).hexdigest(), []).append(f)
    rep["performed"]["duplicate_images"] = sum(len(v) - 1 for v in hashes.values())
    sigs = {}
    for lb in labels:
        sig = json.dumps([lb["merchant_name"], lb["transaction_datetime"], lb["items"], lb["total"]],
                         ensure_ascii=False, sort_keys=True)
        sigs.setdefault(sig, []).append(lb["id"])
    rep["performed"]["duplicate_transactions"] = sum(len(v) - 1 for v in sigs.values())
    # 난이도·템플릿 분포
    dist = {}
    for lb in labels:
        dist.setdefault(lb["source_template_id"], {}).setdefault(lb["difficulty"], 0)
        dist[lb["source_template_id"]][lb["difficulty"]] += 1
    rep["performed"]["difficulty_by_template"] = dist
    tot = {d: sum(v.get(d, 0) for v in dist.values()) for d in ("easy", "medium", "hard")}
    rep["performed"]["difficulty_total"] = tot
    cnts = [len(lb["items"]) for lb in labels]
    rep["performed"]["item_rows_min_max"] = [min(cnts), max(cnts)]
    rep["performed"]["items_with_wrapped_name"] = sum(
        1 for lb in labels for it in lb["items"]
        if not any(it["name"] in l for l in lb["full_text"].split("\n")))
    # 글리프(한글 깨짐) 검사: 사용 폰트 cmap에 모든 문자가 있는지
    try:
        from fontTools.ttLib import TTCollection
        cmap = set()
        for (kind, bold), (fname, i) in FONT_FILES.items():
            cmap |= set(TTCollection(_find(fname)).fonts[i].getBestCmap().keys())
        missing = set()
        for lb in labels:
            for ch_ in lb["full_text"]:
                if ch_ not in "\n" and ord(ch_) not in cmap:
                    missing.add(ch_)
        rep["performed"]["missing_glyph_chars"] = sorted(missing)
    except Exception as e:  # noqa
        rep["not_performed"] = [f"글리프 검사 실패: {e}"]
    return rep


def make_preview(out_dir, labels):
    pick = []
    for tid in sorted(TEMPLATES):
        for d in ("easy", "medium", "hard"):
            lb = next(l for l in labels if l["source_template_id"] == tid and l["difficulty"] == d)
            pick.append(lb)
    tw, th = 230, 380
    cols = 10
    rows = 3
    sheet = Image.new("RGB", (cols * tw, rows * (th + 30) + 50), (255, 255, 255))
    dr = ImageDraw.Draw(sheet)
    dr.text((10, 10), "합성 영수증 미리보기 (열: T01~T10, 행: easy / medium / hard)  —  SYNTHETIC / 테스트용",
            font=font("sans", 22, True), fill=(0, 0, 0))
    for k, lb in enumerate(pick):
        c, rr = k // 3, k % 3
        im = Image.open(os.path.join(out_dir, "images", lb["id"] + ".png"))
        im.thumbnail((tw - 10, th))
        x, y = c * tw + 5, 50 + rr * (th + 30)
        sheet.paste(im, (x, y))
        dr.text((x, y + th + 4), f"{lb['id']} {lb['source_template_id']} {lb['difficulty']}",
                font=font("sans", 15), fill=(0, 0, 0))
    sheet.save(os.path.join(out_dir, "preview.jpg"), quality=88)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default=".")
    ap.add_argument("--seed", type=int, default=DEFAULT_SEED)
    ap.add_argument("--zip", default=None, help="ZIP 경로(기본: <out>/../synthetic_receipts.zip)")
    args = ap.parse_args()
    out = os.path.abspath(args.out)
    for sub in ("images", "labels"):
        p = os.path.join(out, sub)
        if os.path.exists(p):
            shutil.rmtree(p)
        os.makedirs(p)
    master = random.Random(args.seed)
    np.random.seed(args.seed)
    plan = []
    for tid in sorted(TEMPLATES):
        diffs = DIFF_PLAN[:]
        counts = ITEM_COUNT_PLAN[:]
        master.shuffle(diffs)
        master.shuffle(counts)
        plan += [(tid, d, n) for d, n in zip(diffs, counts)]
    master.shuffle(plan)    # 파일 번호 순서에 템플릿이 몰리지 않게 섞기
    labels, auxes, rows = [], [], []
    for i, (tid, diff, n) in enumerate(plan, start=1):
        rng = random.Random(f"{args.seed}-{i}")
        img, lb, ax = build_one(i, tid, diff, n, rng)
        img.save(os.path.join(out, "images", f"{lb['id']}.png"), optimize=True)
        with open(os.path.join(out, "labels", f"{lb['id']}.json"), "w", encoding="utf-8") as f:
            json.dump(lb, f, ensure_ascii=False, indent=2)
        labels.append(lb)
        auxes.append(ax)
        rows.append([f"{lb['id']}.png", f"{lb['id']}.json", tid, diff, lb["merchant_name"],
                     lb["transaction_datetime"], lb["subtotal"] if lb["subtotal"] is not None else "",
                     lb["discount"] if lb["discount"] is not None else "", lb["total"], lb["payment_method"],
                     len(lb["items"]), img.size[0], img.size[1]])
        if i % 20 == 0:
            print(f"{i}/200 생성")
    with open(os.path.join(out, "manifest.csv"), "w", encoding="utf-8-sig", newline="") as f:
        w = csv.writer(f)
        w.writerow(["image", "label", "source_template_id", "difficulty", "merchant_name", "transaction_datetime",
                    "subtotal", "discount", "total", "payment_method", "item_rows", "width", "height"])
        w.writerows(rows)
    with open(os.path.join(out, "templates.json"), "w", encoding="utf-8") as f:
        json.dump(SOURCES, f, ensure_ascii=False, indent=2)
    make_preview(out, labels)
    rep = validate(out, labels, auxes)
    with open(os.path.join(out, "validation_report.json"), "w", encoding="utf-8") as f:
        json.dump(rep, f, ensure_ascii=False, indent=2)
    print(json.dumps(rep["performed"], ensure_ascii=False, indent=1))
    if rep["issues"]:
        print("ISSUES:", *rep["issues"][:30], sep="\n  ")
    zpath = args.zip or os.path.join(os.path.dirname(out), "synthetic_receipts.zip")
    with zipfile.ZipFile(zpath, "w", zipfile.ZIP_DEFLATED) as z:
        for root, _, files in os.walk(out):
            for fn in files:
                if fn.endswith(".pyc"):
                    continue
                full = os.path.join(root, fn)
                z.write(full, os.path.join("synthetic_receipts", os.path.relpath(full, out)))
    print("ZIP:", zpath)


if __name__ == "__main__":
    main()
