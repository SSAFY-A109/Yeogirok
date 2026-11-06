"""사진 1장 원인 진단: 방향 → OCR 원문 → 필드별 추적 → GPU 사용 증거.

출력 폴더 (원본 사진·모델은 건드리지 않음, 서버 안에만 둘 것)
  ocr_input.png        OCR 에 실제로 넘긴 배열 (전처리 후, 무손실)
  ocr_input_small.jpg  같은 이미지 축소본 (방향 확인용)
  ocr_boxes.jpg        OCR 박스 + 번호 (빨강: 신뢰도 0.5 미만)
  orientation.json     EXIF 태그, PIL 기준 방향과의 차이 (0/90/180/270)
  ocr_raw.json         OCR 전체 결과 (텍스트·좌표·신뢰도, 검출 파라미터) ← 원문 포함
  lines.json           줄 묶기 결과 ← 원문 포함
  trace.json           필드별 추적 (인식 여부·후보·탈락 규칙) ← 원문 포함
  gpu.json             Paddle 장치, 할당 메모리 변화, nvidia-smi, 예측기 장치 설정
  report.md            요약 (카드·승인·전화·사업자번호, 대표자 이름 가림)
"""
from __future__ import annotations

import io
import json
import os
import re
import time

import cv2
import numpy as np

from . import imageio, resources

# ---------- 가리기 ----------

def mask_text(t: str) -> str:
    t = re.sub(r"\d{3}-\d{2}-\d{5}", "***-**-*****", t)                        # 사업자번호
    t = re.sub(r"\d{2,4}\s*[-)]\s*\d{3,4}\s*-\s*\d{4}", "***-****-****", t)      # 전화
    t = re.sub(r"\d{4}[-\s]?[\d*]{2,4}[-\s]?\*+[-*\s\d]*", "****", t)            # 카드 (가려진 형식)
    t = re.sub(r"\d{6,}", lambda m: "*" * len(m.group()), t)                      # 승인·가맹점 번호 등
    # 사람 이름: '대표자:', 줄이 갈려 '자:'만 남은 경우, 성명·계산원·담당·판매원·캐셔·서명 뒤 한글 2~4자
    t = re.sub(r"((?:대\s*표\s*자?|표\s*자|(?<![가-힣])자|성\s*명|계\s*산\s*원|담\s*당\s*자?|판\s*매\s*원|캐\s*셔|서\s*명)"
               r"\s*[:：]?\s*)([가-힣]{2,4})", lambda m: m.group(1) + "○" * len(m.group(2)), t)
    return t


# ---------- 방향 ----------

def orientation_check(raw: bytes, decoded: np.ndarray) -> dict:
    from PIL import Image, ImageOps
    tag = imageio.read_exif_orientation(raw)
    ref = cv2.cvtColor(np.array(ImageOps.exif_transpose(Image.open(io.BytesIO(raw))).convert("RGB")), cv2.COLOR_RGB2BGR)
    small = lambda m: cv2.resize(cv2.cvtColor(m, cv2.COLOR_BGR2GRAY), (240, 320) if m.shape[0] >= m.shape[1] else (320, 240))
    rots = {0: ref, 90: cv2.rotate(ref, cv2.ROTATE_90_CLOCKWISE), 180: cv2.rotate(ref, cv2.ROTATE_180),
            270: cv2.rotate(ref, cv2.ROTATE_90_COUNTERCLOCKWISE)}
    diffs = {}
    for k, r in rots.items():
        if r.shape[:2] == decoded.shape[:2]:
            diffs[k] = round(float(np.abs(small(decoded).astype(int) - small(r).astype(int)).mean()), 2)
    best = min(diffs, key=diffs.get) if diffs else None
    verdict = {0: "정상: EXIF 회전이 한 번 적용됨 (PIL 기준과 같음)", 180: "180도 어긋남: 회전이 반대로 또는 두 번 적용됨",
               90: "90도 어긋남: 회전 누락 또는 잘못된 방향", 270: "270도 어긋남: 회전 누락 또는 잘못된 방향",
               None: "비교 불가 (크기가 다름)"}[best]
    return {"exif_orientation": tag, "decoded_hw": list(decoded.shape[:2]), "pil_reference_hw": list(ref.shape[:2]),
            "mean_abs_diff_by_rotation": diffs, "best_match_rotation": best, "verdict": verdict}


# ---------- GPU 증거 ----------

def _pp_options(root, limit=4000):
    """PaddleOCR 안쪽 예측기들의 pp_option(장치 설정)을 찾는다 (가능한 경우만)."""
    seen, out, stack = set(), [], [(root, 0)]
    while stack and len(seen) < limit:
        obj, d = stack.pop()
        if id(obj) in seen or d > 6:
            continue
        seen.add(id(obj))
        pp = getattr(obj, "pp_option", None) if not isinstance(obj, (str, bytes, int, float)) else None
        if pp is not None:
            out.append({"owner": type(obj).__name__, "device_type": getattr(pp, "device_type", None),
                        "device_id": getattr(pp, "device_id", None), "run_mode": getattr(pp, "run_mode", None)})
        try:
            vals = list(vars(obj).values())
        except TypeError:
            vals = list(obj.values()) if isinstance(obj, dict) else (list(obj) if isinstance(obj, (list, tuple)) else [])
        stack += [(v, d + 1) for v in vals if not isinstance(v, (str, bytes, int, float, bool, type(None), np.ndarray))]
    return out


def gpu_evidence_before():
    ev = {"cuda_visible_devices": os.getenv("CUDA_VISIBLE_DEVICES")}
    try:
        import paddle
        ev["paddle_get_device"] = paddle.device.get_device()
        ev["compiled_with_cuda"] = bool(paddle.device.is_compiled_with_cuda())
        if ev["compiled_with_cuda"] and paddle.device.cuda.device_count():
            ev["gpu0_name_in_process"] = paddle.device.cuda.get_device_properties(0).name
    except Exception as e:
        ev["error"] = f"{type(e).__name__}: {e}"
    ev["paddle_memory_before"] = resources.paddle_gpu_memory()
    return ev


# ---------- 필드 추적 ----------

NEAR_DATE = re.compile(r"(?<!\d)\d{2,4}\s*[-./년]\s*[0-9A-Za-zㅇ○]{1,2}\s*[-./월]\s*[0-9A-Za-zㅇ○]{1,2}(?![0-9])")
NEAR_TIME = re.compile(r"(?<!\d)\d{1,2}\s*[:;.시]\s*\d{2}(?!\d)")


def explain_not_name(rp, text: str):
    """receipt_parser._looks_like_name 과 같은 순서로 탈락 이유를 돌려준다 (통과면 None)."""
    if re.search(r"\d{1,3},\d{3}", text):
        return "금액(1,000 형식) 포함"
    if text.strip().upper() in ("DATE", "NO", "NO."):
        return "DATE/NO"
    if re.search(r"\d{6,}", text):
        return "6자리 이상 숫자"
    if re.fullmatch(r"[\d.\s]*\(\S{1,6}(동|읍|면|리|가)\)", text):
        return "주소 꼬리"
    if rp.ADDR_RE.search(text):
        return "주소 줄 (ADDR_RE)"
    w = next((w for w in rp.NOISE_WORDS if w in text), None)
    if w:
        return f"제외 단어 '{w}'"
    if rp.DATE_RE.search(text) or rp.TIME_RE.search(text):
        return "날짜·시간 포함"
    if not (len(re.findall(r"[가-힣A-Za-z]", text)) >= 2 and len(text) <= 30):
        return "글자 수 조건 (한글·영문 2자 이상, 30자 이하)"
    return None


def trace_name(rp, lines, chosen, expect=None):
    label_hits = []
    for i, (t, _) in enumerate(lines):
        v = rp._after_label(t, rp.PLACE_LABELS)
        if v:
            label_hits.append({"line": i, "value": v})
    rows = []
    for i, (t, s) in enumerate(lines[:12]):
        joined = rp._join_wrapped(t, lines, i)
        c = re.sub(r"\s+", " ", re.sub(r"[\[\]<>*=]+", " ", joined).strip(" -").replace("_", " "))
        if hasattr(rp, "_clean_name_v0r2"):
            c = rp._clean_name_v0r2(c)
        m = re.match(r"^(.{2,25}?점(?:#\d+|\([^)]*\))?)(?=\s|$)", c)
        if m and c[m.end():].strip():
            c = m.group(1)
        why = explain_not_name(rp, c)
        consistent = (why is None) == rp._looks_like_name(c)
        rows.append({"line": i, "raw": t, "joined_next": joined != t, "candidate": c, "score": round(s, 3),
                     "rejected_by": why, "branch": bool(re.search(r"점\b|점[#(]|점\)", c)),
                     "corp": bool(re.search(r"\(주\)|㈜", c)), "rule_check_consistent": consistent})
    passed = [r for r in rows if r["rejected_by"] is None]
    if label_hits:
        rule = "상호명 라벨 뒤 값 (1순위)"
    elif any(r["branch"] for r in passed):
        rule = "라벨 없음 → 상단 12줄 중 지점명('…점') 줄 1순위, 그중 가장 위 줄"
    elif any(r["corp"] for r in passed):
        rule = "라벨 없음 → 법인 표기('(주)') 줄 2순위"
    elif passed:
        rule = "라벨 없음 → 상단 후보 중 첫 줄"
    else:
        rule = "후보 없음"
    out = {"chosen": chosen, "rule": rule, "label_hits": label_hits, "top12": rows}
    if expect:
        g = re.sub(r"\s", "", expect)
        out["expected_in_ocr_lines"] = [i for i, (t, _) in enumerate(lines) if g in re.sub(r"\s", "", t)]
        pieces = [i for i, (t, _) in enumerate(lines) if len(re.sub(r"\s", "", t)) >= 2 and re.sub(r"\s", "", t) in g]
        out["expected_pieces_lines"] = pieces
    return out


def trace_datetime(rp, raw_texts, lines, chosen_date, chosen_time, expect_date=None, expect_time=None):
    from .fields import datetime_candidates
    cands = datetime_candidates(lines)
    near_d = [m.group() for t in raw_texts for m in NEAR_DATE.finditer(t) if not rp.DATE_RE.search(m.group())]
    near_t = [m.group() for t in raw_texts for m in NEAR_TIME.finditer(t) if not rp.TIME_RE.search(m.group())]
    labeled = [{"line": i, "text": t} for i, (t, _) in enumerate(lines) if any(k in t for k in rp.DATETIME_LABELS)]
    out = {"chosen": {"date": chosen_date, "time": chosen_time}, "regex_candidates": cands,
           "near_miss_date_tokens": near_d, "near_miss_time_tokens": near_t, "lines_with_datetime_label": labeled}
    digits = lambda s: re.sub(r"\D", "", s or "")
    joined = digits(" ".join(raw_texts))
    if expect_date:
        y, m, d = expect_date.split("-")
        forms = {y + m + d, y[2:] + m + d}
        out["expected_date_digits_in_ocr"] = any(f in joined for f in forms)
    if expect_time:
        out["expected_time_digits_in_ocr"] = digits(expect_time) in joined
    has_d = any(c["dates"] for c in cands)
    has_t = any(c["time"] for c in cands)
    out["verdict"] = {
        "date": ("후보 있음 → 선택은 후처리 규칙" if has_d else
                 "정규식에 맞는 날짜가 OCR 결과에 없음 → OCR 인식 단계" + (" (비슷한 토큰 있음: 글자 오인식)" if near_d else "")),
        "time": ("후보 있음 → 선택은 후처리 규칙" if has_t else
                 "정규식에 맞는 시간이 OCR 결과에 없음 → OCR 인식 단계" + (" (비슷한 토큰 있음: 글자 오인식)" if near_t else "")),
    }
    return out


def trace_address(lines, policy, expect=None):
    import address_parser as ap
    cands = ap.find_candidates(lines, policy)
    looks = []
    for i, (t, _) in enumerate(lines):
        if ap.SIDO_RE.search(t) or re.search(r"주\s*소|소재지", t):
            hit = ap._line_candidate(t, i, policy)
            looks.append({"line": i, "text": t,
                          "parsed": None if hit is None else hit[1].to_dict(),
                          "why_not": None if hit else "시·도 뒤에 시·군·구 또는 도로명을 읽지 못함 (또는 시·도 없음)"})
    r = ap.extract_address_detail(lines, policy)
    out = {"chosen": r.address, "complete": r.complete, "candidates": [c.to_dict() for c in cands],
           "address_like_lines": looks}
    if expect:
        g = re.sub(r"\s", "", expect)
        out["expected_in_ocr_lines"] = [i for i, (t, _) in enumerate(lines) if g in re.sub(r"\s", "", t)]
    if not looks:
        out["verdict"] = "주소처럼 보이는 줄(시·도 이름 또는 '주소' 라벨)이 OCR 결과에 없음 → OCR 인식 단계 문제"
    elif not cands:
        out["verdict"] = "주소처럼 보이는 줄은 있으나 시·군·구·도로명을 읽지 못함 → 글자 오인식 또는 규칙 문제 (줄 내용 확인)"
    else:
        out["verdict"] = "후보 있음"
    return out


# ---------- 실행 ----------

def draw_boxes(img, tokens, max_side=1600):
    s = min(1.0, max_side / max(img.shape[:2]))
    vis = cv2.resize(img, (int(img.shape[1] * s), int(img.shape[0] * s))) if s < 1 else img.copy()
    for i, t in enumerate(tokens):
        pts = (np.array(t.poly) * s).astype(np.int32)
        col = (0, 0, 255) if t.score < 0.5 else (0, 160, 0)
        cv2.polylines(vis, [pts], True, col, 2)
        cv2.putText(vis, str(i), tuple(int(v) for v in pts[0]), cv2.FONT_HERSHEY_SIMPLEX, 0.5, (255, 0, 0), 1)
    return vis


def run(image: str, out_dir: str, cfg: dict, allow_download=False, also_cpu=False, expect=None) -> dict:
    import receipt_parser as rp
    from .engine import OcrEngine, convert_result, gpu_sync, to_lines
    from .fields import extract
    from .settings import address_policy
    expect = expect or {}
    if os.path.abspath(out_dir) == os.path.abspath(os.path.dirname(image)):
        raise ValueError("진단 폴더는 사진 폴더와 달라야 합니다")
    if os.path.isdir(out_dir) and os.listdir(out_dir):
        raise ValueError(f"{out_dir} 가 비어 있지 않습니다 (덮어쓰지 않음)")
    os.makedirs(out_dir, exist_ok=True)
    W = lambda name, obj: json.dump(obj, open(os.path.join(out_dir, name), "w", encoding="utf-8"),
                                    ensure_ascii=False, indent=1, default=str)

    raw = open(image, "rb").read()
    decoded, dmeta = imageio.decode(raw, cfg["decode"]["exif_mode"])
    ori = orientation_check(raw, decoded)
    img, pmeta = imageio.preprocess(decoded, cfg)
    cv2.imwrite(os.path.join(out_dir, "ocr_input.png"), img)
    small, _ = imageio.resize_keep_ratio(img, max_side=1000)
    cv2.imwrite(os.path.join(out_dir, "ocr_input_small.jpg"), small)
    W("orientation.json", {**ori, "decode": dmeta, "preprocess": pmeta})

    gpu = gpu_evidence_before()
    eng = OcrEngine(cfg["ocr"], allow_download, cfg["expect"]["paddleocr_version"])
    gpu["paddle_memory_after_init"] = resources.paddle_gpu_memory()
    t = time.perf_counter()
    res = eng.ocr.predict(img)[0]
    gpu["sync"] = gpu_sync(eng.device)
    first_ms = (time.perf_counter() - t) * 1000
    gpu["paddle_memory_after_predict"] = resources.paddle_gpu_memory()
    gpu["nvidia_smi_after_predict"] = resources.nvidia_smi(resources.physical_gpu_index(), os.getpid())
    t = time.perf_counter()
    eng.ocr.predict(img)
    gpu_sync(eng.device)
    second_ms = (time.perf_counter() - t) * 1000
    try:
        gpu["predictor_devices"] = _pp_options(eng.ocr)
    except Exception as e:
        gpu["predictor_devices"] = f"확인 실패: {type(e).__name__}"
    out = convert_result(res, first_ms, gpu["sync"])
    get = res.get if hasattr(res, "get") else (lambda k, d=None: d)
    det_params = get("text_det_params")
    dt = get("dt_polys")
    W("ocr_raw.json", {"texts": [x.text for x in out.tokens], "scores": [x.score for x in out.tokens],
                       "polys": [x.poly for x in out.tokens], "dt_polys_count": None if dt is None else len(dt),
                       "text_det_params": det_params, "doc_angle": out.doc_angle, "ocr_kwargs": eng.kwargs})
    cv2.imwrite(os.path.join(out_dir, "ocr_boxes.jpg"), draw_boxes(img, out.tokens))
    lines = to_lines(out)
    W("lines.json", [{"i": i, "text": t_, "score": round(s, 4)} for i, (t_, s) in enumerate(lines)])
    resp, diag = extract(lines, cfg)
    raw_texts = [x.text for x in out.tokens]
    trace = {"response": resp,
             "placeName": trace_name(rp, lines, resp["placeName"], expect.get("name")),
             "datetime": trace_datetime(rp, raw_texts, lines, resp["date"], resp["time"], expect.get("date"), expect.get("time")),
             "address": trace_address(lines, address_policy(cfg), expect.get("address"))}
    W("trace.json", trace)

    cpu = None
    if also_cpu:
        # CPU 비교 실패가 GPU 진단 결과를 막지 않게 한다. oneDNN(MKLDNN) 실행기 오류를 피하려고 enable_mkldnn=False
        try:
            from paddleocr import PaddleOCR
            from .engine import build_kwargs
            kw = build_kwargs(dict(cfg["ocr"], device="cpu"))
            kw["enable_mkldnn"] = False
            t = time.perf_counter()
            r2 = convert_result(PaddleOCR(**kw).predict(img)[0])
            cpu_ms = (time.perf_counter() - t) * 1000
            gt, ct = [x.text for x in out.tokens], [x.text for x in r2.tokens]
            cpu = {"kwargs": kw, "tokens_gpu": len(gt), "tokens_cpu": len(ct),
                   "gpu_texts_also_in_cpu": sum(1 for a_ in gt if a_ in set(ct)),
                   "mean_score_gpu": round(float(np.mean([x.score for x in out.tokens])), 3) if gt else None,
                   "mean_score_cpu": round(float(np.mean([x.score for x in r2.tokens])), 3) if ct else None,
                   "cpu_ms_including_init": round(cpu_ms), "cpu_response": extract(to_lines(r2), cfg)[0]}
            W("cpu_compare.json", {**cpu, "cpu_texts": ct})
        except Exception as e:
            cpu = {"error": f"{type(e).__name__}: {str(e)[:300]}"}
            W("cpu_compare.json", cpu)
    gpu.update({"first_predict_ms": round(first_ms), "second_predict_ms": round(second_ms)})
    W("gpu.json", gpu)
    report = make_report(image, cfg, eng, dmeta, pmeta, ori, gpu, out, lines, trace, det_params, cpu,
                         None if dt is None else len(dt))
    open(os.path.join(out_dir, "report.md"), "w", encoding="utf-8").write(report)
    return {"response": resp, "out_dir": out_dir}


def make_report(image, cfg, eng, dmeta, pmeta, ori, gpu, out, lines, trace, det_params, cpu, dt_count=None) -> str:
    M = mask_text
    sc = [x.score for x in out.tokens]
    mem_b = (gpu.get("paddle_memory_after_init") or {}).get("max_memory_allocated_mib")
    mem_a = (gpu.get("paddle_memory_after_predict") or {}).get("max_memory_allocated_mib")
    smi = gpu.get("nvidia_smi_after_predict") or {}
    L = [f"# OCR 진단 보고서 (가림 처리) — {os.path.basename(image)}", "",
         "## 1. 입력·방향",
         f"- EXIF 방향 {ori['exif_orientation']}, 디코딩 {dmeta['exif_mode']} → {ori['decoded_hw']} (h, w)",
         f"- PIL 기준과 회전별 차이 {ori['mean_abs_diff_by_rotation']} → **{ori['verdict']}**",
         f"- 전처리 {pmeta['steps'] or '없음'}, OCR 입력 {pmeta['input_hw']} (h, w), BGR uint8",
         f"- 검출 파라미터 (PaddleOCR 결과): {det_params}", "",
         "## 2. GPU 사용 증거",
         f"- CUDA_VISIBLE_DEVICES={gpu.get('cuda_visible_devices')}, paddle.get_device()={gpu.get('paddle_get_device')}, "
         f"프로세스 안 GPU 이름={gpu.get('gpu0_name_in_process')}",
         f"- Paddle 할당 최대치: 초기화 후 {mem_b} MiB → 추론 후 {mem_a} MiB",
         f"- nvidia-smi 의 이 PID 사용량: " + (f"{smi.get('process_used_mib')} MiB ({smi.get('process_note') or '목록에 보임'})"
                                              if smi.get("available") else "nvidia-smi 없음"),
         f"- 예측기 장치 설정: {gpu.get('predictor_devices')}",
         f"- 추론 시간: 첫 번째 {gpu.get('first_predict_ms')} ms, 두 번째 {gpu.get('second_predict_ms')} ms (동기화 {gpu.get('sync')})", "",
         "## 3. OCR 결과",
         f"- 검출 박스 {dt_count}개 → 인식 결과 {len(out.tokens)}개, "
         f"평균 신뢰도 {round(float(np.mean(sc)), 3) if sc else None}, 0.5 미만 {sum(s < 0.5 for s in sc)}개",
         f"- 줄 묶기 후 {len(lines)}줄. 상단 15줄 (가림):"]
    L += [f"  - {i}: {M(t)} ({s:.2f})" for i, (t, s) in enumerate(lines[:15])]
    n, d, a = trace["placeName"], trace["datetime"], trace["address"]
    L += ["", "## 4. 필드별 추적",
          f"### 상호명 → `{n['chosen']}`", f"- 선택 규칙: {n['rule']}", f"- 라벨 일치: {n['label_hits'] or '없음'}"]
    L += [f"  - 줄 {r['line']}: `{M(r['candidate'])}` → " +
          ("통과" + (" [지점명]" if r["branch"] else "") + (" [법인]" if r["corp"] else "") if r["rejected_by"] is None
           else f"탈락: {r['rejected_by']}") + (" (다음 줄 붙임)" if r["joined_next"] else "") +
          ("" if r["rule_check_consistent"] else " ⚠규칙 설명 불일치") for r in n["top12"]]
    if "expected_in_ocr_lines" in n:
        L.append(f"- 기대 상호명이 통째로 있는 줄: {n['expected_in_ocr_lines']}, 조각이 있는 줄: {n['expected_pieces_lines']}")
    L += [f"### 날짜·시간 → {d['chosen']}", f"- 날짜: {d['verdict']['date']}", f"- 시간: {d['verdict']['time']}",
          f"- 정규식 후보: {d['regex_candidates']}",
          f"- 형식이 비슷한데 정규식에 안 맞은 토큰: 날짜 {d['near_miss_date_tokens']} / 시간 {d['near_miss_time_tokens']}",
          f"- 날짜 라벨이 있는 줄: {[ (x['line'], M(x['text'])) for x in d['lines_with_datetime_label']]}"]
    for k in ("expected_date_digits_in_ocr", "expected_time_digits_in_ocr"):
        if k in d:
            L.append(f"- {k}: {d[k]}")
    L += [f"### 주소 → `{a['chosen']}` (complete {a['complete']})", f"- 판정: {a['verdict']}"]
    L += [f"  - 줄 {x['line']}: `{M(x['text'])}` → " + ("읽은 부분 " + json.dumps({k: x['parsed'][k] for k in ('sido', 'sgg', 'road', 'building')}, ensure_ascii=False)
                                                   if x["parsed"] else x["why_not"]) for x in a["address_like_lines"]]
    if cpu and "error" in cpu:
        L += ["", "## 5. 같은 입력을 CPU 로", f"- CPU 비교 실패 (GPU 진단에는 영향 없음): {cpu['error']}"]
    elif cpu:
        L += ["", "## 5. 같은 입력을 CPU 로 (GPU 결과 이상 여부 확인, oneDNN 끔)",
              f"- 박스 GPU {cpu['tokens_gpu']} / CPU {cpu['tokens_cpu']}, GPU 텍스트 중 CPU 에도 똑같이 있는 것 {cpu['gpu_texts_also_in_cpu']}",
              f"- 평균 신뢰도 GPU {cpu['mean_score_gpu']} / CPU {cpu['mean_score_cpu']}, CPU 시간(초기화 포함) {cpu['cpu_ms_including_init']} ms",
              f"- CPU 응답: {cpu['cpu_response']}"]
    L += ["", "※ 원문이 들어 있는 ocr_raw.json·lines.json·trace.json 은 서버 밖으로 옮기지 않는다."]
    return "\n".join(L) + "\n"
