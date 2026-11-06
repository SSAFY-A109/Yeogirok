"""인식 모델 CER. 검수된 줄 단위 정답이 있을 때만 계산한다.

라벨: PaddleOCR rec 형식 txt ('줄 이미지 경로\\t정답') + 같은 폴더의 export_meta.json (sources 기록)
- sources 가 human_verified 만이면 계산
- synthetic_generated / public_dataset 은 --allow-source 로 명시해야 계산하고, 결과에 그 종류를 표시한다
- auto_candidate 가 섞였거나 출처 기록이 없으면 계산하지 않고 이유를 출력한다
"""
from __future__ import annotations

import json
import os


def edit_distance(a, b) -> int:
    prev = list(range(len(b) + 1))
    for i, ca in enumerate(a, 1):
        cur = [i]
        for j, cb in enumerate(b, 1):
            cur.append(min(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + (ca != cb)))
        prev = cur
    return prev[-1]


def cer(preds, golds) -> dict:
    errs = sum(edit_distance(p, g) for p, g in zip(preds, golds))
    chars = sum(len(g) for g in golds)
    ns = lambda s: s.replace(" ", "")
    errs_ns = sum(edit_distance(ns(p), ns(g)) for p, g in zip(preds, golds))
    chars_ns = sum(len(ns(g)) for g in golds)
    return {"lines": len(golds), "chars": chars, "cer": round(100 * errs / chars, 2) if chars else None,
            "cer_nospace": round(100 * errs_ns / chars_ns, 2) if chars_ns else None,
            "line_acc": round(100 * sum(p == g for p, g in zip(preds, golds)) / len(golds), 2) if golds else None}


def gate(label_file: str, allow_sources=()) -> tuple[bool, str, list]:
    if not os.path.exists(label_file):
        return False, f"정답 파일이 없음: {label_file} → 줄 단위 정답이 없어 CER 를 계산하지 않음", []
    meta_p = os.path.join(os.path.dirname(os.path.abspath(label_file)), "export_meta.json")
    if not os.path.exists(meta_p):
        return False, f"{meta_p} 가 없어 정답 출처(사람 검수 여부)를 알 수 없음 → CER 계산 안 함", []
    srcs = json.load(open(meta_p, encoding="utf-8")).get("sources") or []
    if "auto_candidate" in srcs:
        return False, "검수 전 OCR 후보(auto_candidate)가 섞여 있음 → CER 계산 안 함", srcs
    extra = [s for s in srcs if s != "human_verified" and s not in allow_sources]
    if extra:
        return False, f"사람이 검수하지 않은 출처 {extra} → --allow-source 로 명시해야 계산", srcs
    if not srcs:
        return False, "출처 기록이 비어 있음 → CER 계산 안 함", srcs
    return True, "", srcs


def run(label_file: str, allow_sources=(), model_name="korean_PP-OCRv5_mobile_rec", model_dir=None,
        device="gpu:0", batch=64) -> dict:
    ok, why, srcs = gate(label_file, allow_sources)
    if not ok:
        print("[CER 건너뜀]", why)
        return {"skipped": why}
    base = os.path.dirname(os.path.abspath(label_file))
    items = [l.split("\t", 1) for l in open(label_file, encoding="utf-8").read().splitlines() if "\t" in l]
    from paddleocr import TextRecognition      # PaddleOCR 3.x 인식 모듈 (eval_rec.py 와 같은 사용법)
    kw = {"model_name": model_name, "device": device}
    if model_dir:
        kw["model_dir"] = model_dir
    model = TextRecognition(**kw)
    preds = []
    for i in range(0, len(items), batch):
        chunk = [os.path.join(base, p) for p, _ in items[i:i + batch]]
        for r in model.predict(input=chunk, batch_size=batch):
            get = r.get if hasattr(r, "get") else (lambda k: r[k])
            preds.append(get("rec_text"))
    res = cer(preds, [g for _, g in items])
    res.update({"label_file": label_file, "sources": srcs, "model": model_dir or model_name,
                "note": None if srcs == ["human_verified"] else f"출처 {srcs}: 실제 영수증 사람 검수 정답이 아님"})
    print(json.dumps(res, ensure_ascii=False))
    return res
