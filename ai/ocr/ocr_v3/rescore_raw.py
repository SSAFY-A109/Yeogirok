"""저장된 OCR 결과(lab.py eval --save-raw 의 raw/*.json)로 필드 추출만 다시 채점한다. OCR·GPU 를 쓰지 않는다.

사용 (서버, ~/receipt_ocr):
  venv/bin/python ocr_v3/rescore_raw.py --labels real_new/dev2_labels.csv \
      --runs results_v3/dev2_1280_raw results_v3/dev2_1280_rec_ft2_raw \
      --set fields.date_lookalike_fix=fallback --out results_v3/rescore_datefix_20261104

- 결과 폴더마다 그 평가 때의 config.json 설정을 그대로 쓰고, --set 으로 준 값만 바꿔 비교한다
- 먼저 '바꾸기 전' 채점이 그 폴더의 rows.csv 판정(사진·필드마다)과 metrics.json 과 같은지 확인한다.
  하나라도 다르면 멈춘다 (저장된 OCR 결과로 같은 채점을 재현하지 못한 것이므로 비교 결과를 믿을 수 없음)
- 출력은 정답 수와 판정 변화(파일명·판정)만. OCR 원문과 추출값은 출력·저장하지 않는다
- holdout 이 들어간 경로는 거부한다 (holdout2 는 최종 후보 확정 뒤 한 번만)
"""
from __future__ import annotations

import argparse
import copy
import csv
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.getcwd())          # receipt_parser, eval_baseline (운영 코드)
sys.path.insert(0, HERE)                 # ocrlab, address_parser(v3), ocr_response(v3)

from ocrlab.evaluate import FIELDS, gold_values, judge_field, load_labels, summarize   # noqa: E402
from ocrlab.fields import extract                                                       # noqa: E402
from ocrlab.settings import DEFAULTS, _parse_value, load_config, set_key                # noqa: E402

KEYS = ("date", "time", "name", "address")


def saved_config(run_dir: str) -> dict:
    """평가 때 설정(config.json)을 기본값 위에 덮는다. 그때 없던 새 키는 기본값(운영과 같음)."""
    cfg = load_config()
    saved = json.load(open(os.path.join(run_dir, "config.json"), encoding="utf-8"))
    for sect in DEFAULTS:
        for k in DEFAULTS[sect]:
            if k in (saved.get(sect) or {}):
                cfg[sect][k] = saved[sect][k]
    return cfg


def score(lines_by_file, gold, addr_given, cfg):
    rows, actions = [], {}
    for name, lines in lines_by_file.items():
        resp, diag = extract(lines, cfg)
        gv = gold_values(gold[name], addr_given)
        row = {"file": name, "repeat": 0, "status": resp["status"],
               "addressComplete_pred": resp["addressComplete"], "addressComplete_gold": gv["address_complete"]}
        for fld, key in FIELDS:
            row[fld] = judge_field(fld, resp[key], gv[fld])
        rows.append(row)
        n_act = len(diag["datetime"]["actions"]) + (1 if diag["placeName"].get("action") else 0)
        if n_act:
            actions[name] = n_act
    return rows, actions


def brief(s: dict) -> dict:
    out = {k: {"exact": s[k]["exact"], "gold_present": s[k]["gold_present"], "evaluated": s[k]["evaluated"]} for k in KEYS}
    out.update({k: s[k] for k in ("all3_fields_ok_rate", "all4_fields_ok_rate", "addressComplete_acc")})
    out["SUCCESS"] = s["status"]["SUCCESS"]
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--labels", required=True)
    ap.add_argument("--runs", nargs="+", required=True, help="lab.py eval --save-raw 결과 폴더")
    ap.add_argument("--set", action="append", required=True, help="바꿀 설정 key=value (예: fields.date_lookalike_fix=fallback)")
    ap.add_argument("--out", help="요약 저장 폴더 (새 폴더만. 원문 없음)")
    a = ap.parse_args()
    if any("holdout" in p for p in a.runs + [a.labels]):
        sys.exit("[멈춤] holdout 은 최종 후보 확정 뒤 lab.py eval --final 로 한 번만 평가합니다.")
    if a.out and os.path.exists(a.out):
        sys.exit(f"[멈춤] {a.out} 이 이미 있어요 (덮어쓰지 않음).")
    gold, addr_given = load_labels([a.labels])
    from receipt_parser import boxes_from_polys, group_lines

    report, ok_all = {"labels": a.labels, "set": a.set, "runs": {}}, True
    for run in a.runs:
        raw_dir = os.path.join(run, "raw")
        if not os.path.isdir(raw_dir):
            print(f"\n== {run}: raw 폴더 없음 (--save-raw 로 평가한 폴더가 아님) → 건너뜀")
            continue
        base = saved_config(run)
        new = copy.deepcopy(base)
        for s in a.set:
            k, v = s.split("=", 1)
            set_key(new, k.strip(), _parse_value(v))
        changed = {k: new[k.split(".")[0]][k.split(".")[1]] for k in (s.split("=")[0].strip() for s in a.set)}

        lines_by_file = {}
        for f in sorted(os.listdir(raw_dir)):
            if f.endswith(".json"):
                r = json.load(open(os.path.join(raw_dir, f), encoding="utf-8"))
                lines_by_file[f[:-5]] = group_lines(boxes_from_polys(r["texts"], r["scores"], r["polys"]))
        missing = [n for n in lines_by_file if n not in gold]
        if missing:
            sys.exit(f"[멈춤] {run}: 정답표에 없는 사진 {len(missing)}장")

        rows0, _ = score(lines_by_file, gold, addr_given, base)
        rows1, acts = score(lines_by_file, gold, addr_given, new)

        # 1) 재현 확인: rows.csv 판정과 사진·필드마다 같아야 한다
        saved_rows = {r["file"]: r for r in csv.DictReader(open(os.path.join(run, "rows.csv"), encoding="utf-8-sig"))
                      if str(r.get("repeat", "0")) == "0"}
        diff = [(r["file"], k, saved_rows.get(r["file"], {}).get(k), r[k]) for r in rows0 for k in KEYS + ("status",)
                if saved_rows.get(r["file"], {}).get(k) != r[k]]
        if len(saved_rows) != len(rows0):
            diff.append(("(사진 수)", "n", len(saved_rows), len(rows0)))
        s0, s1 = summarize(rows0), summarize(rows1)
        m = json.load(open(os.path.join(run, "metrics.json"), encoding="utf-8"))["fields"]
        mdiff = [k for k in KEYS if m[k]["exact"] != s0[k]["exact"]] + \
                [k for k in ("all3_fields_ok_rate", "all4_fields_ok_rate") if m[k] != s0[k]]
        print(f"\n== {run}  ({len(rows0)}장, 바꾼 설정 {changed})")
        if diff or mdiff:
            ok_all = False
            print(f"  [멈춤] 바꾸기 전 채점이 저장된 결과와 다름 → 이 폴더 비교는 믿을 수 없음 (다른 칸 {len(diff)}개, metrics {mdiff})")
            for d in diff[:10]:
                print("   ", d)
            report["runs"][run] = {"reproduced": False, "diff_cells": len(diff), "metrics_diff": mdiff}
            continue
        print("  재현 확인: 바꾸기 전 채점 = rows.csv 판정·metrics.json (사진·필드 모두 같음)")

        b0, b1 = brief(s0), brief(s1)
        for k, lab in zip(KEYS, ("날짜", "시간", "상호명", "주소")):
            print(f"  {lab}: {b0[k]['exact']}/{b0[k]['gold_present']} → {b1[k]['exact']}/{b1[k]['gold_present']}"
                  f"  (평가 {b0[k]['evaluated']}장)")
        print(f"  3필드 {b0['all3_fields_ok_rate']}% → {b1['all3_fields_ok_rate']}%  | 4필드 {b0['all4_fields_ok_rate']}% → "
              f"{b1['all4_fields_ok_rate']}%  | SUCCESS {b0['SUCCESS']} → {b1['SUCCESS']}")
        changes = [{"file": r0["file"], "field": k, "before": r0[k], "after": r1[k]}
                   for r0, r1 in zip(rows0, rows1) for k in KEYS + ("status",) if r0[k] != r1[k]]
        print(f"  보정이 동작한 사진 {len(acts)}장 / 판정이 바뀐 칸 {len(changes)}개")
        for c in changes:
            print(f"    {c['file']}  {c['field']}: {c['before']} → {c['after']}")
        report["runs"][run] = {"reproduced": True, "n_images": len(rows0), "changed_settings": changed,
                               "before": b0, "after": b1, "fixed_images": sorted(acts), "judgement_changes": changes}
    if a.out:
        os.makedirs(a.out)
        json.dump(report, open(os.path.join(a.out, "rescore.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=2)
        print(f"\n요약 저장: {a.out}/rescore.json (OCR 원문·추출값 없음)")
    sys.exit(0 if ok_all else 2)


if __name__ == "__main__":
    main()
