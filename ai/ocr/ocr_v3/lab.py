#!/usr/bin/env python3
"""영수증 OCR 실험 진입점 (운영 API 와 별도). ~/receipt_ocr 에서 실행: venv/bin/python ocr_v3/lab.py <명령>

명령
  env        서버 환경 점검 (읽기 전용)
  infer      사진 1장 → 응답 JSON (규격 v3). --diag-out 으로 후보·선택 이유 저장
  diagnose   사진 1장 원인 진단 (방향·OCR 원문·필드 추적·GPU 증거, --also-cpu)
  eval       폴더 평가 (필드 정확도·누락·오인식, p50/p95, 초기화 시간, 메모리)
  compare    전처리·방향 설정 비교 (모드마다 프로세스 분리)
  candidates OCR 예측으로 라벨 '후보' 만들기 (검수 전, 학습 금지)
  dataset    목록 검사·통계·분할·det/rec 내보내기·PPOCRLabel 가져오기·검수 반영
  dictcheck  한국어 사전·출력층·사전학습 가중치 호환성
  finetune   공식 학습·평가·내보내기 명령 (기본은 출력만, --run 으로 실행)
  cer        검수된 줄 단위 정답이 있을 때만 CER
"""
from __future__ import annotations

import argparse
import json
import os
import sys
import time

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.getcwd())     # receipt_parser, eval_baseline: ~/receipt_ocr (운영 코드)
sys.path.insert(0, HERE)            # address_parser, ocr_response(v3), ocrlab: 이 폴더가 우선

from ocrlab.settings import MODES  # noqa: E402


def need_project():
    if not os.path.exists("receipt_parser.py"):
        sys.exit("~/receipt_ocr (receipt_parser.py 가 있는 폴더)에서 실행하세요: cd ~/receipt_ocr")


def add_cfg(p):
    p.add_argument("--config", help="설정 YAML (기본: 운영 API 와 같은 baseline)")
    p.add_argument("--mode", default="baseline", choices=list(MODES))
    p.add_argument("--set", action="append", default=[], metavar="KEY=VALUE", help="예: --set ocr.text_det_thresh=0.3")
    p.add_argument("--device", help="예: gpu:0, cpu (CUDA_VISIBLE_DEVICES=2 와 함께 쓰면 실제 2번 GPU)")
    p.add_argument("--allow-download", action="store_true", help="캐시에 없는 방향 모델 다운로드 허용 (기본 금지)")


def cmd_env(a):
    from ocrlab import envcheck
    rep = envcheck.run(".", a.paddle_run_check, a.init_ocr, a.device)
    envcheck.print_report(rep)
    if a.out:
        json.dump(rep, open(a.out, "w", encoding="utf-8"), ensure_ascii=False, indent=2, default=str)
        print("저장:", a.out)


def cmd_infer(a):
    need_project()
    from ocrlab import imageio
    from ocrlab.engine import OcrEngine, to_lines
    from ocrlab.fields import extract
    from ocrlab.settings import load_config
    cfg = load_config(a.config, a.mode, a.set)
    if a.device:
        cfg["ocr"]["device"] = a.device
    raw = open(a.image, "rb").read()
    try:
        eng = OcrEngine(cfg["ocr"], a.allow_download, cfg["expect"]["paddleocr_version"])
    except (RuntimeError, ValueError, FileNotFoundError) as e:
        sys.exit(f"[중단] {e}")
    t0 = time.perf_counter()
    img, dmeta = imageio.decode(raw, cfg["decode"]["exif_mode"])
    img, pmeta = imageio.preprocess(img, cfg)
    out = eng.run(img)
    resp, diag = extract(to_lines(out), cfg)
    total = (time.perf_counter() - t0) * 1000
    print(json.dumps(resp, ensure_ascii=False))
    print(f"[모드 {cfg['_mode']} 변경 {cfg['_changed']}] 초기화 {eng.init_s:.1f}s, 처리 {total:.0f} ms (OCR {out.ocr_ms:.0f}, "
          f"동기화 {out.sync}), 입력 {pmeta['input_hw']}, EXIF {dmeta['exif_orientation']} ({dmeta['exif_applied']}), "
          f"전처리 {pmeta['steps'] or '없음'}" + (f", 경고 {eng.version_warning}" if eng.version_warning else ""),
          file=sys.stderr)
    if a.diag_out:
        os.makedirs(os.path.dirname(os.path.abspath(a.diag_out)), exist_ok=True)
        json.dump({"response": resp, "diagnostics": diag, "decode": dmeta, "preprocess": pmeta,
                   "ocr_kwargs": eng.kwargs}, open(a.diag_out, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
        print("진단 저장 (OCR 원문 포함, 공유 금지):", a.diag_out, file=sys.stderr)
    if a.save_preprocessed:
        print("전처리 이미지:", imageio.save_image(img, a.save_preprocessed, os.path.basename(a.image), a.image),
              file=sys.stderr)


def cmd_diagnose(a):
    need_project()
    from ocrlab import diagnose
    from ocrlab.settings import load_config
    cfg = load_config(a.config, a.mode, a.set)
    if a.device:
        cfg["ocr"]["device"] = a.device
    out = a.out or os.path.join("private_diag", f"{os.path.splitext(os.path.basename(a.image))[0]}_{time.strftime('%Y%m%d_%H%M%S')}")
    expect = {k: getattr(a, f"expect_{k}") for k in ("name", "date", "time", "address") if getattr(a, f"expect_{k}")}
    try:
        r = diagnose.run(a.image, out, cfg, a.allow_download, a.also_cpu, expect)
    except (RuntimeError, ValueError, FileNotFoundError) as e:
        sys.exit(f"[중단] {e}")
    print(json.dumps(r["response"], ensure_ascii=False))
    print(f"진단 폴더: {r['out_dir']}  (요약: report.md / 원문 포함 파일은 서버 밖으로 옮기지 않기)")


def cmd_eval(a):
    need_project()
    from ocrlab.evaluate import run_eval
    run_eval(a)


def cmd_compare(a):
    need_project()
    from ocrlab.evaluate import run_compare
    run_compare(a)


def cmd_candidates(a):
    need_project()
    import cv2
    from ocrlab import dataset, imageio
    from ocrlab.engine import OcrEngine
    from ocrlab.evaluate import list_images
    from ocrlab.settings import load_config
    if os.path.exists(os.path.join(a.out, "AUTO_CANDIDATES_not_verified.jsonl")):
        sys.exit(f"{a.out} 에 후보 파일이 이미 있음 (덮어쓰지 않음)")
    cfg = load_config(None, "baseline", a.set)
    if a.device:
        cfg["ocr"]["device"] = a.device
    eng = OcrEngine(cfg["ocr"], False, cfg["expect"]["paddleocr_version"])
    os.makedirs(os.path.join(a.out, "images"), exist_ok=True)
    items = []
    for p in list_images(a.images):
        img, _ = imageio.decode(open(p, "rb").read(), "explicit")       # EXIF 를 한 번만 반영한 사본에 좌표를 맞춘다
        img, _ = imageio.preprocess(img, cfg)    # 평가·운영과 같은 입력 크기(--set resize.max_side=1280). 저장 사본도 같은 크기라 좌표가 맞음
        rel = f"images/{os.path.splitext(os.path.basename(p))[0]}.jpg"
        cv2.imwrite(os.path.join(a.out, rel), img, [cv2.IMWRITE_JPEG_QUALITY, 95])
        items.append((rel, eng.run(img)))
        print("후보:", rel, len(items[-1][1].tokens), "줄")
    recs = dataset.candidates_from_ocr(items)
    if a.receipt_map:                       # 같은 가게·같은 영수증 묶음 (예: split_real_new 의 group 열)
        import csv
        m = {}
        for r in csv.DictReader(open(a.receipt_map, encoding="utf-8-sig")):
            key = os.path.splitext(os.path.basename(r.get("file") or r.get("image") or ""))[0]
            m[key] = r.get("receipt_id") or r.get("group")
        for r in recs:
            r["receipt_id"] = m.get(r["receipt_id"]) or r["receipt_id"]
    dataset.write_manifest(recs, os.path.join(a.out, "AUTO_CANDIDATES_not_verified.jsonl"))
    with open(os.path.join(a.out, "Label_CANDIDATES_not_verified.txt"), "w", encoding="utf-8") as f:
        for r in recs:                                   # PPOCRLabel 에서 열어 고칠 수 있는 형식
            ann = [{"transcription": l["text"], "points": l["points"], "difficult": False} for l in r["lines"]]
            f.write(f"{r['image']}\t{json.dumps(ann, ensure_ascii=False)}\n")
    print(f"완료: {len(recs)}장. 이 파일들은 정답이 아닙니다 (OCR 원문 포함, 공유 금지). 검수 후 dataset promote / import-ppocrlabel")


def cmd_dataset(a):
    from ocrlab import dataset
    if a.action == "import-ppocrlabel":
        recs = dataset.import_ppocrlabel(a.label, a.source, a.receipt_map)
        dataset.write_manifest(recs, a.out)
        print(f"가져옴: {len(recs)}장 → {a.out} (label_source={a.source})")
        return
    recs = dataset.read_manifest(a.manifest)
    if a.action in ("check", "stats"):
        chars = dataset.load_dict(a.dict)
        rep = dataset.check(recs, a.root, chars, a.max_len, a.near_dup) if a.action == "check" else \
            {"stats": dataset.stats(recs), "errors": [], "warnings": []}
        print(json.dumps(rep["stats"], ensure_ascii=False, indent=1))
        for e in rep["errors"][:200]:
            print("[오류]", e)
        for w in rep["warnings"][:200]:
            print("[경고]", w)
        print(f"오류 {len(rep['errors'])}개, 경고 {len(rep['warnings'])}개")
        if a.report:
            json.dump(rep, open(a.report, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
        sys.exit(1 if rep["errors"] else 0)
    if a.action == "split":
        recs = dataset.split(recs, a.root, tuple(a.ratios), a.seed, a.near_dup)
        dataset.write_manifest(recs, a.out)
        print(json.dumps(dataset.stats(recs)["by_split"], ensure_ascii=False), "→", a.out)
    elif a.action in ("export-det", "export-rec"):
        if any(not r.get("split") for r in recs):
            sys.exit("분할(split)이 없는 사진이 있습니다. 먼저 dataset split")
        if a.action == "export-det":
            meta = dataset.export_det(recs, a.root, a.out, tuple(a.sources))
        else:
            meta = dataset.export_rec(recs, a.root, a.out, tuple(a.sources), dataset.load_dict(a.dict), a.max_len)
        print(json.dumps(meta, ensure_ascii=False))
        if not sum(meta["counts"].values()):
            print(f"[알림] 내보낸 정답이 없습니다. 출처 {a.sources} 이면서 train/val/test 로 나뉜 사진이 없음 "
                  "(검수 전 후보는 review 로 빠짐)")
    elif a.action == "promote":
        recs, n = dataset.promote(recs, a.reviewer)
        dataset.write_manifest(recs, a.out)
        print(f"검수 반영 {n}장 (reviewed: true 만) → {a.out}")


def cmd_dictcheck(a):
    from ocrlab import dictcheck
    rep = dictcheck.run(a.dict, a.inference_yml, a.pretrained, not a.no_space_char, a.labels)
    print(json.dumps(rep, ensure_ascii=False, indent=1))
    sys.exit(0 if rep["ok"] else 1)


def cmd_finetune(a):
    from ocrlab import finetune
    try:
        sys.exit(finetune.run(a.settings, a.action, a.checkpoint, a.run, a.resume))
    except finetune.SetupError as e:
        sys.exit(f"[설정 오류] {e}")


def cmd_cer(a):
    from ocrlab import cer
    cer.run(a.labels, tuple(a.allow_source), a.model_name, a.model_dir, a.device)


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = ap.add_subparsers(dest="cmd", required=True)

    p = sub.add_parser("env")
    p.add_argument("--out", help="JSON 저장 경로")
    p.add_argument("--paddle-run-check", action="store_true")
    p.add_argument("--init-ocr", action="store_true", help="운영 설정으로 OCR 초기화 + 빈 이미지 1장 (모델 캐시 필요)")
    p.add_argument("--device", default="gpu:0")
    p.set_defaults(fn=cmd_env)

    p = sub.add_parser("infer")
    p.add_argument("image")
    add_cfg(p)
    p.add_argument("--diag-out", help="후보·선택 이유 JSON (OCR 원문 포함)")
    p.add_argument("--save-preprocessed", metavar="DIR", help="전처리한 이미지를 이 폴더에 저장 (원본은 그대로)")
    p.set_defaults(fn=cmd_infer)

    p = sub.add_parser("diagnose", help="사진 1장 원인 진단 (방향·OCR 원문·필드 추적·GPU 증거)")
    p.add_argument("image")
    add_cfg(p)
    p.add_argument("--out", help="진단 폴더 (기본: private_diag/<사진이름>_<시각>)")
    p.add_argument("--also-cpu", action="store_true", help="같은 입력을 CPU 로도 돌려 GPU 결과와 비교")
    p.add_argument("--expect-name")
    p.add_argument("--expect-date", help="YYYY-MM-DD")
    p.add_argument("--expect-time", help="HH:MM")
    p.add_argument("--expect-address", help="도로명까지")
    p.set_defaults(fn=cmd_diagnose)

    for name, fn in (("eval", cmd_eval), ("compare", cmd_compare)):
        p = sub.add_parser(name)
        p.add_argument("--images", nargs="+", required=True)
        p.add_argument("--labels", nargs="*", default=[])
        p.add_argument("--data-kind", required=True, choices=("real", "synthetic", "public"))
        p.add_argument("--out", required=True)
        p.add_argument("--warmup", type=int, default=3)
        p.add_argument("--repeat", type=int, default=1)
        p.add_argument("--dry-run", action="store_true", help="OCR 없이 입출력만 점검")
        add_cfg(p)
        if name == "eval":
            p.add_argument("--save-values", action="store_true", help="추출값도 rows.csv 에 저장 (비공개 폴더만)")
            p.add_argument("--diag-dir", help="사진별 진단 JSON 폴더 (OCR 원문 포함)")
            p.add_argument("--save-raw", action="store_true",
                           help="OCR 결과·per_receipt.csv 저장 (classify_errors.py 용, OCR 원문 포함)")
            p.add_argument("--final", action="store_true", help="holdout 1회 평가")
        else:
            p.add_argument("--modes", nargs="+", default=list(MODES), choices=list(MODES))
        p.set_defaults(fn=fn)

    p = sub.add_parser("candidates")
    p.add_argument("--images", nargs="+", required=True)
    p.add_argument("--out", required=True)
    p.add_argument("--receipt-map", help="CSV: file,group (또는 image,receipt_id). 같은 영수증 묶음")
    p.add_argument("--device")
    p.add_argument("--set", action="append", default=[])
    p.set_defaults(fn=cmd_candidates)

    p = sub.add_parser("dataset")
    p.add_argument("action", choices=("check", "stats", "split", "export-det", "export-rec", "import-ppocrlabel", "promote"))
    p.add_argument("--manifest")
    p.add_argument("--root", default=".")
    p.add_argument("--out")
    p.add_argument("--dict", help="문자 사전 (예: PaddleOCR/ppocr/utils/dict/ppocrv5_korean_dict.txt)")
    p.add_argument("--max-len", type=int, default=None)
    p.add_argument("--near-dup", type=int, default=6, help="비슷한 사진 기준 (dHash 해밍 거리)")
    p.add_argument("--ratios", type=float, nargs=3, default=[0.8, 0.1, 0.1])
    p.add_argument("--seed", type=int, default=0)
    p.add_argument("--sources", nargs="+", default=["human_verified"])
    p.add_argument("--label", help="PPOCRLabel Label.txt")
    p.add_argument("--source", default="human_verified")
    p.add_argument("--receipt-map", help="CSV: image,receipt_id")
    p.add_argument("--reviewer")
    p.add_argument("--report")
    p.set_defaults(fn=cmd_dataset)

    p = sub.add_parser("dictcheck")
    p.add_argument("--dict", required=True)
    p.add_argument("--inference-yml")
    p.add_argument("--pretrained")
    p.add_argument("--no-space-char", action="store_true")
    p.add_argument("--labels", nargs="*")
    p.set_defaults(fn=cmd_dictcheck)

    p = sub.add_parser("finetune")
    p.add_argument("--settings", required=True)
    p.add_argument("--action", required=True, choices=("check", "train", "eval", "export", "list"))
    p.add_argument("--checkpoint", default="best", help="best | latest | 파일 경로")
    p.add_argument("--run", action="store_true", help="명령을 실제로 실행")
    p.add_argument("--resume", action="store_true")
    p.set_defaults(fn=cmd_finetune)

    p = sub.add_parser("cer")
    p.add_argument("--labels", required=True)
    p.add_argument("--allow-source", nargs="*", default=[], choices=("synthetic_generated", "public_dataset"))
    p.add_argument("--model-name", default="korean_PP-OCRv5_mobile_rec")
    p.add_argument("--model-dir")
    p.add_argument("--device", default="gpu:0")
    p.set_defaults(fn=cmd_cer)

    a = ap.parse_args(argv)
    if a.cmd == "dataset":
        req = {"import-ppocrlabel": ("label", "out"), "promote": ("manifest", "out", "reviewer"),
               "split": ("manifest", "out"), "export-det": ("manifest", "out"), "export-rec": ("manifest", "out"),
               "check": ("manifest",), "stats": ("manifest",)}[a.action]
        miss = [f"--{r.replace('_', '-')}" for r in req if not getattr(a, r)]
        if miss:
            ap.error(f"dataset {a.action} 에 필요한 옵션: {' '.join(miss)}")
    a.fn(a)


if __name__ == "__main__":
    main()
