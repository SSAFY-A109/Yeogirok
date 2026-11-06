"""상호명 오류 진단: 저장된 OCR 결과(lab.py eval --save-raw 의 raw/*.json)로 상호명이 왜 틀렸는지 사진마다 보여 준다.
OCR·GPU 를 쓰지 않는다. holdout 경로는 거부한다 (holdout 은 최종 1회 평가 전용).

사용 (서버, ~/receipt_ocr):
  venv/bin/python ocr_v3/name_diag.py --run results_v3/dev2_1280_rec_b30_raw --labels real_new/dev2_labels.csv

원인 분류 (classify_errors.py 와 같은 기준)
  - 선택·파싱 : 정답 상호가 OCR 줄 안에 그대로 있음 → 글자는 맞게 읽었는데 다른 줄을 골랐거나 잘못 잘랐다 (규칙 대상)
  - 인식 오류 : 그대로는 없지만 비슷한 글자열이 있음 (CER ≤ 0.34) → 글자를 틀리게 읽었다 (학습 대상)
  - 검출 누락 또는 심한 오인식 : 비슷한 글자열도 없음
출력: 사진마다 정답·예측·판정·원인·고른 이유, 정답이 있는 줄 번호와 위쪽 12줄 (3자리 이상 숫자는 #, 서버 밖으로 옮기지 않기)
"""
from __future__ import annotations

import argparse
import collections
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.getcwd())
sys.path.insert(0, HERE)

from ocrlab.evaluate import gold_values, judge_field, load_labels   # noqa: E402
from ocrlab.fields import extract                                   # noqa: E402
from ocrlab.settings import load_config                             # noqa: E402

MASK = re.compile(r"\d{3,}")


def mask(s: str) -> str:
    return MASK.sub(lambda m: "#" * len(m.group(0)), s or "")


def norm(s: str) -> str:
    return "".join((s or "").split()).lower().replace("㈜", "(주)")


def semiglobal_cer(g: str, t: str) -> float:
    if not g:
        return 0.0
    if not t:
        return 1.0
    prev = [0] * (len(t) + 1)
    for i in range(1, len(g) + 1):
        cur = [i] + [0] * len(t)
        for j in range(1, len(t) + 1):
            cur[j] = min(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + (g[i - 1] != t[j - 1]))
        prev = cur
    return min(prev) / len(g)


def compare(a, cfg, gold, addr_given):
    """기존(legacy)과 v2 를 같은 OCR 줄로 나란히. 줄 끝 L=기존이 고른 줄, V=v2 가 고른 줄, G=정답이 있는 줄"""
    import copy
    from receipt_parser import boxes_from_polys, group_lines
    from ocrlab.fields import name_v2
    c2 = copy.deepcopy(cfg)
    c2["fields"]["name_rule"] = "v2"
    for name in a.compare:
        p = os.path.join(a.run, "raw", name + ".json")
        if not os.path.exists(p):
            print(f"\n### {name}: raw 없음")
            continue
        r = json.load(open(p, encoding="utf-8"))
        lines = group_lines(boxes_from_polys(r["texts"], r["scores"], r["polys"]))
        texts = [t for t, _ in lines]
        g = gold_values(gold[name], addr_given)["name"]
        p1 = extract(lines, cfg)[0]["placeName"]
        p2, why2 = name_v2(lines)
        f2 = extract(lines, c2)[0]["placeName"]
        print(f"\n### {name}")
        print(f"  정답: {g}\n  기존: {p1}  ({judge_field('name', p1, g)})\n  v2  : {p2}  ← {why2}  → 최종 {f2} ({judge_field('name', f2, g)})")
        gn = norm(g or "")
        for i, t in enumerate(texts[:a.top]):
            m = ("G" if gn and gn in norm(t) else " ") + ("L" if p1 and norm(p1) in norm(t) else " ") + ("V" if p2 and norm(p2) in norm(t) else " ")
            print(f"   {m} {i:>2} {mask(t)[:70]}")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--run", required=True, help="--save-raw 로 평가한 결과 폴더")
    ap.add_argument("--labels", required=True)
    ap.add_argument("--top", type=int, default=12, help="보여 줄 위쪽 줄 수")
    ap.add_argument("--out", help="요약 JSON 저장 경로 (선택)")
    ap.add_argument("--compare", nargs="*", help="이 사진들만 기존 규칙과 상호명 v2 를 나란히 보여 줌 (파일 이름)")
    a = ap.parse_args()
    if "holdout" in a.run or "holdout" in a.labels:
        sys.exit("[멈춤] holdout 은 진단에 쓰지 않아요 (최종 1회 평가 전용).")
    from receipt_parser import boxes_from_polys, group_lines
    cfg = load_config()
    saved = json.load(open(os.path.join(a.run, "config.json"), encoding="utf-8"))
    for sect in ("fields", "address"):
        for k, v in (saved.get(sect) or {}).items():
            if k in cfg[sect]:
                cfg[sect][k] = v
    gold, addr_given = load_labels([a.labels])
    if a.compare is not None:
        compare(a, cfg, gold, addr_given)
        return
    raw_dir = os.path.join(a.run, "raw")
    cnt, judge_cnt, report = collections.Counter(), collections.Counter(), []
    for f in sorted(os.listdir(raw_dir)):
        if not f.endswith(".json"):
            continue
        name = f[:-5]
        r = json.load(open(os.path.join(raw_dir, f), encoding="utf-8"))
        lines = group_lines(boxes_from_polys(r["texts"], r["scores"], r["polys"]))
        resp, diag = extract(lines, cfg)
        g = gold_values(gold[name], addr_given)["name"]
        j = judge_field("name", resp["placeName"], g)
        judge_cnt[j] += 1
        if j in ("exact", "null_ok", "skip"):
            continue
        texts = [t for t, _ in lines]
        gn = norm(g or "")
        hit = [i for i, t in enumerate(texts) if gn and gn in norm(t)]
        if hit:
            cause, cer = "선택·파싱", 0.0
        else:
            cer = round(min((semiglobal_cer(gn, norm(t)) for t in texts), default=1.0), 3)
            cause = "인식 오류" if cer <= 0.34 else "검출 누락 또는 심한 오인식"
        cnt[cause] += 1
        pred = resp["placeName"]
        pidx = [i for i, t in enumerate(texts) if pred and norm(pred) in norm(t)]
        why = next((c["reason"] for c in diag["placeName"]["candidates"] if c["value"] == pred), "-")
        report.append({"file": name, "judge": j, "cause": cause, "cer": cer, "gold": g, "pred": pred, "why": why,
                       "gold_line": hit[:3], "pred_line": pidx[:3]})
        print(f"\n### {name}  판정 {j}  원인 {cause}" + (f" (CER {cer})" if cause != "선택·파싱" else ""))
        print(f"  정답: {g}\n  예측: {pred}   ← 고른 이유: {why}, 줄 {pidx[:3] or '-'}")
        print(f"  정답이 있는 줄: {hit[:3] or '없음'}")
        for i, t in enumerate(texts[:a.top]):
            mark = "G" if i in hit else " "
            mark += "P" if i in pidx else " "
            print(f"   {mark} {i:>2} {mask(t)[:60]}")
    print("\n== 요약")
    print("  상호명 판정:", dict(judge_cnt))
    print("  틀린 원인:", dict(cnt))
    if a.out:
        json.dump({"run": a.run, "judge": judge_cnt, "cause": cnt, "items": [{k: v for k, v in x.items() if k not in ("gold", "pred")}
                   for x in report]}, open(a.out, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
        print(f"  저장: {a.out} (상호 문자열 없음)")


if __name__ == "__main__":
    main()
