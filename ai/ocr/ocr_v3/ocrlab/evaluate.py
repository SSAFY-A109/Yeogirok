"""평가: 필드별 정확 일치율·누락률·오인식률, 전체 필드 정답률, 처리 시간 p50/p95, 초기화 시간, 메모리.

- 정답표(CSV): file, date, time, merchant_name, address(또는 address_road), address_complete 또는 address_type(선택)
    · address_type: 건물번호 → addressComplete 정답 true / 도로명만·지번·없음 → false / 판독불가 → 평가 제외
    · 어느 칸이든 '판독불가' = 사진에 있지만 읽을 수 없음 → 그 필드만 평가에서 뺀다
    · date/time/merchant_name 빈칸 = 영수증에 없음 (기존 eval_real 규칙)
    · address 열이 모든 행에서 비어 있으면 주소는 '정답 미입력'으로 보고 평가하지 않는다
    · address 를 입력했다면 '-' 는 주소 없음, 빈칸은 그 행만 평가 제외
- 결과: <out>/metrics.json, <out>/rows.csv (정오·시간만. 추출값은 --save-values 일 때만), <out>/config.json
- --data-kind 를 꼭 적는다 (real/synthetic/public). 합성 결과는 실제 영수증 성능으로 표시하지 않는다
- holdout 이 들어간 경로는 --final 이 있어야 돌고, 결과 폴더가 있으면 다시 돌지 않는다 (한 번만 평가)
- --save-raw: OCR 결과(raw/<파일>.json)와 per_receipt.csv 를 eval_real.py 와 같은 형식으로 저장 → 기존 classify_errors.py 로
  상호명·날짜·시간 오류 원인을 나눌 수 있다. 주소 오류 원인은 address_error_causes.csv (판정만). OCR 원문 포함 → 비공개
"""
from __future__ import annotations

import csv
import json
import os
import platform
import re
import statistics
import subprocess
import sys
import time

from . import imageio, resources
from .settings import load_config

IMG_EXT = (".jpg", ".jpeg", ".png", ".bmp", ".webp")
FIELDS = (("date", "date"), ("time", "time"), ("name", "placeName"), ("address", "address"))
UNREADABLE = "판독불가"     # 사진에 있지만 읽을 수 없는 값 → 그 필드는 평가에서 뺀다 (빈칸 = 사진에 없음 과 구분)


def list_images(paths):
    out = []
    for p in paths:
        if os.path.isdir(p):
            out += [os.path.join(p, f) for f in sorted(os.listdir(p)) if f.lower().endswith(IMG_EXT)]
        elif os.path.isfile(p) and p.lower().endswith(IMG_EXT):
            out.append(p)
        else:
            raise FileNotFoundError(f"이미지 경로가 없습니다: {p}")
    return out


def load_labels(paths) -> tuple[dict, bool]:
    gold = {}
    for p in paths or []:
        if not os.path.exists(p):
            raise FileNotFoundError(f"정답표가 없습니다: {p}")
        for r in csv.DictReader(open(p, encoding="utf-8-sig")):
            gold[r["file"]] = r
    addr_given = any(((r.get("address") or r.get("address_road") or "").strip()) for r in gold.values())
    return gold, addr_given


def gold_values(g: dict, addr_given: bool) -> dict:
    t = (g.get("time") or "").strip()
    t = "SKIP" if t == UNREADABLE else (t[:5] or None)
    a = (g.get("address") or g.get("address_road") or "").strip()
    addr = "SKIP" if not addr_given or a in ("", UNREADABLE) else (None if a == "-" else a)
    sk = lambda v: "SKIP" if v == UNREADABLE else (v or None)
    ac = (g.get("address_complete") or "").strip().lower()
    if not ac:                                   # address_complete 열이 없으면 address_type 으로 정한다
        ac = {"건물번호": "true", "도로명만": "false", "지번": "false", "없음": "false"}.get((g.get("address_type") or "").strip(), "")
    return {"date": sk((g.get("date") or "").strip()), "time": t,
            "name": sk((g.get("merchant_name") or "").strip()), "address": addr,
            "address_complete": None if ac == "" else ac in ("1", "true", "y", "yes", "o")}


def judge_field(fld: str, pred, gold) -> str:
    if gold == "SKIP":
        return "skip"
    if gold is None:
        return "null_ok" if pred is None else "false_pos"
    if pred is None:
        return "miss"
    if fld == "address":
        n = lambda s: re.sub(r"\s+", " ", s).strip()
        return "exact" if n(pred) == n(gold) else "wrong"
    from eval_baseline import judge          # 기존 평가와 같은 상호명 정규화·부분일치 규칙
    if fld == "name":                        # ㈜ 한 글자와 (주) 를 같게 본다 (인쇄 방식 차이)
        pred, gold = pred.replace("㈜", "(주)"), gold.replace("㈜", "(주)")
    return judge(pred, gold, "merchant" if fld == "name" else fld)


def tall_boxes(tokens, factor: float = 3.0) -> int:
    """박스 높이가 중앙값의 factor 배를 넘는 수 (11/02 진단: 원본 4000px 에서 여러 줄이 한 박스로 합쳐짐)."""
    hs = [max(p[1] for p in t.poly) - min(p[1] for p in t.poly) for t in tokens]
    if len(hs) < 5:
        return 0
    med = statistics.median(hs)
    return sum(h > factor * med for h in hs)


def presence(rows: list[dict]) -> dict:
    first = [r for r in rows if r["repeat"] == 0]
    out = {k: sum(r[f"has_{k}"] for r in first) for k in ("placeName", "date", "time", "address")}
    out["address_complete"] = sum(r["address_complete"] for r in first)
    out["status"] = {s: sum(r["status"] == s for r in first) for s in ("SUCCESS", "PARTIAL", "FAILED")}
    out["images_with_tall_boxes"] = sum(r["n_tall_boxes"] > 0 for r in first)
    out["n"] = len(first)
    return out


def summarize(rows: list[dict]) -> dict:
    out = {}
    for fld, _ in FIELDS:
        js = [r[fld] for r in rows if r[fld] != "skip"]
        n_gold = sum(j in ("exact", "partial", "wrong", "miss") for j in js)
        c = {k: js.count(k) for k in ("exact", "partial", "wrong", "miss", "null_ok", "false_pos")}
        rate = (lambda x: round(100 * x / n_gold, 1) if n_gold else None)
        out[fld] = {"evaluated": len(js), "gold_present": n_gold, **c,
                    "exact_rate": rate(c["exact"]), "miss_rate": rate(c["miss"]),
                    "misread_rate": rate(c["wrong"] + c["partial"]),   # 값은 냈지만 정답과 다름 (부분일치 포함)
                    "skipped": len(rows) - len(js)}
    ok = ("exact", "null_ok")
    three = [r for r in rows if all(r[f] != "skip" for f in ("date", "time", "name"))]   # 판독불가가 있는 사진은 뺀다
    out["all3_fields_ok_rate"] = (round(100 * sum(all(r[f] in ok for f in ("date", "time", "name")) for r in three) / len(three), 1)
                                  if three else None)
    out["all3_evaluated"] = len(three)
    four = [r for r in rows if all(r[f] != "skip" for f in ("date", "time", "name", "address"))]
    out["all4_fields_ok_rate"] = (round(100 * sum(all(r[f] in ok for f in ("date", "time", "name", "address")) for r in four)
                                  / len(four), 1) if four else None)
    out["all4_evaluated"] = len(four)
    ac = [r for r in rows if r.get("addressComplete_gold") is not None]
    out["addressComplete_acc"] = (round(100 * sum(r["addressComplete_pred"] == r["addressComplete_gold"] for r in ac) / len(ac), 1)
                                  if ac else None)
    out["status"] = {s: sum(r["status"] == s for r in rows) for s in ("SUCCESS", "PARTIAL", "FAILED")}
    return out


def env_info(engine) -> dict:
    info = {"python": platform.python_version(), "platform": platform.platform()}
    try:
        import cv2
        info["opencv"] = cv2.__version__
    except Exception:
        pass
    try:
        import paddle
        info["paddle"] = paddle.__version__
        info["paddle_cuda_compiled"] = bool(paddle.device.is_compiled_with_cuda())
    except Exception as e:
        info["paddle"] = f"불러오기 실패: {type(e).__name__}"
    if engine is not None:
        info["paddleocr"] = engine.version
        info["paddleocr_warning"] = engine.version_warning
        info["ocr_kwargs"] = engine.kwargs
        info["optional_models_cached"] = engine.optional_models
    return info


def run_eval(args) -> dict:
    if any("holdout" in p for p in args.images) and not args.final:
        sys.exit("holdout 은 정답표·합격 기준을 정한 뒤 --final 로 한 번만 평가합니다.")
    if args.final and os.path.exists(os.path.join(args.out, "metrics.json")):
        sys.exit(f"{args.out}/metrics.json 이 이미 있음: holdout 은 한 번만 평가합니다.")
    cfg = load_config(args.config, args.mode, args.set)
    if args.device:
        cfg["ocr"]["device"] = args.device
    files = list_images(args.images)
    gold, addr_given = load_labels(args.labels)
    if gold:
        files = [f for f in files if os.path.basename(f) in gold]
    if not files:
        sys.exit("평가할 이미지가 없습니다 (정답표에 있는 파일만 평가).")
    if not gold:
        print("[알림] 정답표 없음 → 정확도는 계산하지 않고 시간·메모리·상태만 기록합니다.")
    if not addr_given:
        print("[알림] 주소 정답이 입력되지 않아 주소 정확도는 건너뜁니다.")

    from .engine import OcrEngine, to_lines
    from .fields import extract
    try:
        engine = None if args.dry_run else OcrEngine(cfg["ocr"], args.allow_download, cfg["expect"]["paddleocr_version"])
    except (RuntimeError, ValueError, FileNotFoundError) as e:
        sys.exit(f"[중단] {e}")
    init_s = None if engine is None else engine.init_s
    mem_after_init = resources.snapshot(engine is not None)

    def one(path):
        raw = open(path, "rb").read()
        t0 = time.perf_counter()
        img, dmeta = imageio.decode(raw, cfg["decode"]["exif_mode"])
        t1 = time.perf_counter()
        img, pmeta = imageio.preprocess(img, cfg)
        t2 = time.perf_counter()
        ometa = {"triggered": False, "angle": 0}
        if engine is None:
            from .engine import OcrOutput
            out = OcrOutput([], 0.0, None)
        else:
            out = engine.run(img)
            if cfg["pre"].get("orient_retry", "legacy") == "auto":
                from . import orient
                out, ometa = orient.run(engine, img, out)
        pmeta["orient"] = ometa
        t3 = time.perf_counter()
        resp, diag = extract(to_lines(out), cfg)
        t4 = time.perf_counter()
        tm = {"decode_ms": (t1 - t0) * 1000, "pre_ms": (t2 - t1) * 1000, "ocr_ms": (t3 - t2) * 1000,
              "post_ms": (t4 - t3) * 1000, "total_ms": (t4 - t0) * 1000}
        return resp, diag, tm, dmeta, pmeta, out

    for _ in range(args.warmup):                      # 워밍업 (통계 제외)
        one(files[0])
    rows, diags, raw_rows, addr_causes = [], {}, [], []
    for rep in range(args.repeat):
        for f in files:
            resp, diag, tm, dmeta, pmeta, out = one(f)
            name = os.path.basename(f)
            row = {"file": name, "repeat": rep, "status": resp["status"],
                   **{k: round(v, 1) for k, v in tm.items()}, "sync": out.sync,
                   "decoded_hw": "x".join(map(str, dmeta["decoded_hw"])), "ocr_input_hw": "x".join(map(str, pmeta["input_hw"])),
                   "exif_orientation": dmeta["exif_orientation"], "n_tokens": len(out.tokens),
                   "n_tall_boxes": tall_boxes(out.tokens),             # 여러 줄이 한 박스로 합쳐진 것으로 의심되는 박스 수
                   **{f"has_{k}": resp[k] is not None for k in ("placeName", "date", "time", "address")},
                   "address_complete": resp["addressComplete"],
                   "orient_retry": int(pmeta["orient"]["triggered"]), "orient_angle": pmeta["orient"]["angle"]}
            if gold:
                gv = gold_values(gold[name], addr_given)
                for fld, key in FIELDS:
                    row[fld] = judge_field(fld, resp[key], gv[fld])
                row["addressComplete_pred"] = resp["addressComplete"]
                row["addressComplete_gold"] = gv["address_complete"]
            if args.save_values:
                row.update({f"pred_{k}": resp[k] for k in ("placeName", "date", "time", "address", "addressComplete")})
            rows.append(row)
            if args.diag_dir and rep == 0:
                diags[name] = diag
            if args.save_raw and rep == 0 and gold:
                os.makedirs(os.path.join(args.out, "raw"), exist_ok=True)
                t, s_, p_ = out.as_triplet()
                json.dump({"texts": t, "scores": s_, "polys": p_}, open(os.path.join(args.out, "raw", name + ".json"), "w",
                          encoding="utf-8"), ensure_ascii=False)
                raw_rows.append({"file": name, "gold_date": gv["date"], "pred_date": resp["date"], "date": row["date"],
                                 "gold_time": gv["time"], "pred_time": resp["time"], "time": row["time"],
                                 "gold_name": gv["name"], "pred_name": resp["placeName"], "name": row["name"]})
                if row["address"] != "skip":
                    from .triage import ACTION, classify
                    cat, score = classify(row["address"], gv["address"], t)
                    addr_causes.append({"file": name, "field": "address", "judge": row["address"], "category": cat,
                                        "score": score, "action": ACTION[cat]})
    mem_end = resources.snapshot(engine is not None)

    os.makedirs(args.out, exist_ok=True)
    keys = list(dict.fromkeys(k for r in rows for k in r))
    with open(os.path.join(args.out, "rows.csv"), "w", encoding="utf-8-sig", newline="") as fp:
        w = csv.DictWriter(fp, fieldnames=keys)
        w.writeheader()
        w.writerows(rows)
    for fname, rr in (("per_receipt.csv", raw_rows), ("address_error_causes.csv", addr_causes)):
        if rr:
            with open(os.path.join(args.out, fname), "w", encoding="utf-8-sig", newline="") as fp:
                w = csv.DictWriter(fp, fieldnames=list(rr[0]))
                w.writeheader()
                w.writerows(rr)
    if args.diag_dir:                                    # OCR 원문이 들어감 → 비공개 폴더에만
        os.makedirs(args.diag_dir, exist_ok=True)
        for k, v in diags.items():
            json.dump(v, open(os.path.join(args.diag_dir, k + ".json"), "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    ms = [r["total_ms"] for r in rows]
    ocr_ms = [r["ocr_ms"] for r in rows]
    long_sides = [max(map(int, r["ocr_input_hw"].split("x"))) for r in rows]
    metrics = {
        "data_kind": args.data_kind,
        "note": "합성 데이터 결과 — 실제 영수증 성능이 아님" if args.data_kind == "synthetic" else None,
        "mode": cfg["_mode"], "changed_vs_baseline": cfg["_changed"],
        "n_images": len(files), "repeat": args.repeat, "samples": len(rows), "warmup_runs": args.warmup,
        "fields": summarize(rows) if gold else None,
        "timing_ms": {"p50": resources.percentile(ms, 50), "p95": resources.percentile(ms, 95),
                      "mean": round(statistics.mean(ms), 1), "ocr_p50": resources.percentile(ocr_ms, 50),
                      "ocr_p95": resources.percentile(ocr_ms, 95), "sync": rows[0]["sync"],
                      "scope": "디코딩+전처리+OCR+후처리 (HTTP 전송 제외)"},
        "init_s": None if init_s is None else round(init_s, 2),
        "input_long_side": {"min": min(long_sides), "median": statistics.median(long_sides), "max": max(long_sides)},
        "orient": {"mode": cfg["pre"].get("orient_retry", "legacy"),
                   "retried_n": sum(r["orient_retry"] for r in rows if r["repeat"] == 0),
                   "rotated_n": sum(r["orient_angle"] != 0 for r in rows if r["repeat"] == 0)},
        "presence": presence(rows),        # 정답 없이도 볼 수 있는 값: 필드가 채워진 사진 수 (정확도 아님)
        "memory": {"after_init": mem_after_init, "end": mem_end},
        "env": env_info(engine), "dry_run": args.dry_run,
    }
    json.dump(metrics, open(os.path.join(args.out, "metrics.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=2)
    json.dump({k: v for k, v in cfg.items()}, open(os.path.join(args.out, "config.json"), "w", encoding="utf-8"),
              ensure_ascii=False, indent=2)
    print_summary(metrics)
    return metrics


def print_summary(m: dict):
    head = f"[{m['mode']}] {m['data_kind']} {m['n_images']}장 x{m['repeat']}"
    if m["note"]:
        head += f"  ※ {m['note']}"
    print(head)
    if m["fields"]:
        f = m["fields"]
        for fld, label in (("date", "날짜"), ("time", "시간"), ("name", "상호명"), ("address", "주소")):
            x = f[fld]
            if x["evaluated"] == 0:
                print(f"  {label}: 평가 안 함 (정답 미입력)")
                continue
            print(f"  {label}: 정확 {x['exact_rate']}%  누락 {x['miss_rate']}%  오인식 {x['misread_rate']}%  "
                  f"(정답 있음 {x['gold_present']}, 정답 없음 {x['null_ok'] + x['false_pos']})")
        print(f"  3필드 정답률 {f['all3_fields_ok_rate']}%  4필드 {f['all4_fields_ok_rate']}%  status {f['status']}")
    pr = m["presence"]
    print(f"  채워진 필드 (정확도 아님, {pr['n']}장 중): 상호 {pr['placeName']} 날짜 {pr['date']} 시간 {pr['time']} "
          f"주소 {pr['address']} (건물번호까지 {pr['address_complete']}) | status {pr['status']} | 큰 박스 있는 사진 {pr['images_with_tall_boxes']}")
    t = m["timing_ms"]
    print(f"  시간 p50 {t['p50']} ms / p95 {t['p95']} ms (OCR p50 {t['ocr_p50']}), 초기화 {m['init_s']} s, 동기화 {t['sync']}")


def run_compare(args):
    """모드마다 프로세스를 따로 띄워 같은 조건으로 비교한다 (GPU 메모리·초기화 시간이 섞이지 않게)."""
    lab = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "lab.py")
    results = {}
    for mode in args.modes:
        out = os.path.join(args.out, mode)
        cmd = [sys.executable, lab, "eval", "--mode", mode, "--out", out, "--data-kind", args.data_kind,
               "--warmup", str(args.warmup), "--repeat", str(args.repeat), "--images", *args.images]
        if args.labels:
            cmd += ["--labels", *args.labels]
        for opt, val in (("--config", args.config), ("--device", args.device)):
            if val:
                cmd += [opt, val]
        for s in args.set or []:
            cmd += ["--set", s]
        if args.allow_download:
            cmd.append("--allow-download")
        if args.dry_run:
            cmd.append("--dry-run")
        print("$", " ".join(cmd))
        if subprocess.run(cmd).returncode != 0:
            print(f"[실패] {mode}")
            continue
        results[mode] = json.load(open(os.path.join(out, "metrics.json"), encoding="utf-8"))
    rows = []
    for mode, m in results.items():
        f = m["fields"] or {}
        g = lambda k: (f.get(k) or {}).get("exact_rate")
        rows.append({"mode": mode, "changed": json.dumps(m["changed_vs_baseline"], ensure_ascii=False),
                     "date": g("date"), "time": g("time"), "name": g("name"), "address": g("address"),
                     "all3": f.get("all3_fields_ok_rate"), "all4": f.get("all4_fields_ok_rate"),
                     "p50_ms": m["timing_ms"]["p50"], "p95_ms": m["timing_ms"]["p95"], "init_s": m["init_s"],
                     **{f"filled_{k}": m["presence"][k] for k in ("placeName", "date", "time", "address", "address_complete")},
                     "SUCCESS": m["presence"]["status"]["SUCCESS"], "images_with_tall_boxes": m["presence"]["images_with_tall_boxes"]})
    if rows:
        with open(os.path.join(args.out, "compare.csv"), "w", encoding="utf-8-sig", newline="") as fp:
            w = csv.DictWriter(fp, fieldnames=list(rows[0]))
            w.writeheader()
            w.writerows(rows)
        print(f"\n비교표: {args.out}/compare.csv (같은 사진·같은 정답표, 모드마다 한 요소만 다름)")
        for r in rows:
            print(f"  {r['mode']:<14} 정확도 날짜 {r['date']} 시간 {r['time']} 상호 {r['name']} 주소 {r['address']} 3필드 {r['all3']} "
                  f"| 채워짐 상호 {r['filled_placeName']} 날짜 {r['filled_date']} 시간 {r['filled_time']} 주소 {r['filled_address']} "
                  f"(완전 {r['filled_address_complete']}) SUCCESS {r['SUCCESS']} 큰박스 {r['images_with_tall_boxes']} "
                  f"| p50 {r['p50_ms']} p95 {r['p95_ms']}")
