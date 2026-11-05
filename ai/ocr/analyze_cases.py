#!/usr/bin/env python3
"""사례 분석 2가지 (OCR 재실행 없음, 저장된 결과만 읽음).

[1] 줄 정리 전후 사례 (compare_postprocess.py 와 같은 조건)
    전: OCR 원본 박스 순서 + 필드 파서 / 후: 기울기 보정·정렬·줄 병합 + 같은 파서
    → 판정이 바뀐 영수증만 골라 정답·전 예측·후 예측을 표로 저장
[2] oracle vs ocr 원인 분리 (합성 200장)
    oracle = 정답 텍스트(완벽한 인식 + 완벽한 줄)를 파서에 넣은 결과
    ocr    = 실제 OCR 결과를 파서에 넣은 결과
    필드별로: 둘 다 정답 / OCR 쪽만 오답(→ 인식·검출·줄 정리 원인) / oracle도 오답(→ 파서 규칙 원인)

출력: case_analysis/
  postprocess_cases.csv   판정이 바뀐 사례 (실제 영수증 포함 → 비공개용)
  oracle_vs_ocr.csv       필드별 원인 분류 전체
  summary.md              요약 (공개용: 실제 영수증은 상호명·날짜·시간만 표시)
"""
import csv
import json
import os
from collections import Counter
from pathlib import Path

from receipt_parser import boxes_from_polys, group_lines, parse_receipt

FIELDS = ("date", "time", "name")
OUT = Path("case_analysis")


def judge(pred, gold, field):
    if gold is None or gold == "":
        return "gold_null"
    if pred is None:
        return "miss"
    if field == "name":
        p, g = "".join(pred.split()).lower(), "".join(gold.split()).lower()
        if p == g:
            return "exact"
        return "partial" if p and (p in g or g in p) else "wrong"
    return "exact" if pred == gold else "wrong"


def preds(r):
    return {"date": r.date, "time": r.time, "name": r.placeName}


def postprocess_cases():
    rows = []
    for folder, key in (("results_real", "file"), ("results_ocr", "id")):
        for row in csv.DictReader(open(Path(folder) / "per_receipt.csv", encoding="utf-8-sig")):
            raw = json.load(open(Path(folder) / "raw" / (row[key] + ".json"), encoding="utf-8"))
            before = preds(parse_receipt(list(zip(raw["texts"], raw["scores"]))))
            after = preds(parse_receipt(group_lines(boxes_from_polys(raw["texts"], raw["scores"], raw["polys"]))))
            for f in FIELDS:
                jb, ja = judge(before[f], row["gold_" + f], f), judge(after[f], row["gold_" + f], f)
                if jb != ja:
                    rows.append({"dataset": "실제" if folder == "results_real" else "합성", "sample": row[key],
                                 "template": row.get("template", ""), "difficulty": row.get("difficulty", ""),
                                 "field": f, "gold": row["gold_" + f], "before_pred": before[f], "before": jb,
                                 "after_pred": after[f], "after": ja,
                                 "change": "개선" if ja == "exact" else ("악화" if jb == "exact" else "변경")})
    return rows


def oracle_vs_ocr():
    o = {r["id"]: r for r in csv.DictReader(open("results_oracle/per_receipt.csv", encoding="utf-8-sig"))}
    rows = []
    for r in csv.DictReader(open("results_ocr/per_receipt.csv", encoding="utf-8-sig")):
        orc = o[r["id"]]
        for f in FIELDS:
            a, b = orc[f], r[f]
            if a == "gold_null":
                cause = "정답 없음"
            elif a == "exact" and b == "exact":
                cause = "둘 다 정답"
            elif a == "exact":
                cause = "OCR 쪽 원인 (인식·검출·줄 정리)"
            else:
                cause = "파서 규칙 원인 (정답 텍스트로도 실패)"
            rows.append({"id": r["id"], "template": r["template"], "difficulty": r["difficulty"], "field": f,
                         "gold": r["gold_" + f], "oracle_pred": orc["pred_" + f], "oracle": a,
                         "ocr_pred": r["pred_" + f], "ocr": b, "cause": cause})
    return rows


def write_csv(path, rows):
    if not rows:
        open(path, "w").write("")
        return
    with open(path, "w", encoding="utf-8-sig", newline="") as fp:
        w = csv.DictWriter(fp, fieldnames=list(rows[0].keys()))
        w.writeheader()
        w.writerows(rows)


def main():
    OUT.mkdir(exist_ok=True)
    md = ["# 사례 분석 (저장된 OCR 결과로 재계산, OCR 재실행 없음)", ""]

    pc = postprocess_cases()
    write_csv(OUT / "postprocess_cases.csv", pc)
    md += ["## 1. 줄 정리 전후로 판정이 바뀐 사례", "",
           "| 데이터 | 샘플 | 필드 | 정답 | 적용 전 예측 | 적용 후 예측 | 변화 |", "|---|---|---|---|---|---|---|"]
    for r in pc:
        md.append(f"| {r['dataset']} | {r['sample']} | {r['field']} | {r['gold']} | {r['before_pred']} ({r['before']}) "
                  f"| {r['after_pred']} ({r['after']}) | {r['change']} |")
    cnt = Counter((r["dataset"], r["change"]) for r in pc)
    md += ["", "건수: " + ", ".join(f"{d} {c} {n}건" for (d, c), n in sorted(cnt.items())), ""]

    if os.path.exists("results_oracle/per_receipt.csv"):
        ov = oracle_vs_ocr()
        write_csv(OUT / "oracle_vs_ocr.csv", ov)
        md += ["## 2. oracle vs ocr 원인 분리 (합성 200장)", "",
               "| 필드 | 둘 다 정답 | OCR 쪽 원인 | 파서 규칙 원인 |", "|---|---|---|---|"]
        for f in FIELDS:
            c = Counter(r["cause"] for r in ov if r["field"] == f)
            md.append(f"| {f} | {c['둘 다 정답']} | {c['OCR 쪽 원인 (인식·검출·줄 정리)']} | "
                      f"{c['파서 규칙 원인 (정답 텍스트로도 실패)']} |")
        md += ["", "### 오답 목록", "", "| id | 템플릿 | 필드 | 정답 | oracle 예측 | ocr 예측 | 원인 |",
               "|---|---|---|---|---|---|---|"]
        for r in ov:
            if r["cause"] not in ("둘 다 정답", "정답 없음"):
                md.append(f"| {r['id']} | {r['template']} | {r['field']} | {r['gold']} | {r['oracle_pred']} "
                          f"| {r['ocr_pred']} | {r['cause']} |")
        md += ["", "주의: 'OCR 쪽 원인'은 글자 인식·텍스트 검출·줄 정리 중 무엇인지까지는 구분하지 않는다. "
               "oracle 은 정답 줄을 그대로 쓰므로 줄 정리 오류도 OCR 쪽에 포함된다."]
    else:
        md += ["## 2. oracle 결과 없음", "", "먼저 `python eval_baseline.py --data ./synthetic --mode oracle --out results_oracle` 실행"]

    (OUT / "summary.md").write_text("\n".join(md), encoding="utf-8")
    print("\n".join(md))
    print(f"\n저장: {OUT}/summary.md, postprocess_cases.csv, oracle_vs_ocr.csv")


if __name__ == "__main__":
    main()
