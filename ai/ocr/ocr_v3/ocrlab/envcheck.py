"""GPU 서버 환경 점검 (읽기 전용). 패키지 설치·모델 다운로드·프로세스 재시작을 하지 않는다.

확인 항목: 프로젝트 경로와 진입점, 실행 중인 API, Python·PaddlePaddle·PaddleOCR·PaddleX·OpenCV 버전,
GPU 모델·메모리·드라이버, Paddle CUDA 빌드·GPU 인식, OpenCV 의 EXIF 처리, 캐시된 모델, 데이터·학습 자료.
--paddle-run-check : paddle.utils.run_check() (작은 GPU 연산)
--init-ocr         : 운영과 같은 설정으로 PaddleOCR 를 한 번 만들고 빈 이미지 1장 추론 (모델이 캐시에 있어야 함)
"""
from __future__ import annotations

import glob
import importlib
import json
import os
import platform
import re
import subprocess
import sys

from . import imageio, resources

FLOW = ("POST /ocr/receipt (multipart file, 10MB 이하) → app.decode: cv2.imdecode(IMREAD_COLOR) → "
        "PaddleOCR.predict (eval_baseline.run_ocr_factory 설정) → receipt_parser.group_lines·boxes_from_polys → "
        "parse_receipt (상호명·날짜·시간 + 주소) → ocr_response.to_response → JSON")


def _ver(mod):
    try:
        m = importlib.import_module(mod)
        return getattr(m, "__version__", "버전 정보 없음")
    except Exception as e:
        return f"없음/오류: {type(e).__name__}"


def _sh(cmd, timeout=15):
    try:
        return subprocess.run(cmd, capture_output=True, text=True, timeout=timeout).stdout.strip()
    except Exception as e:
        return f"실패: {type(e).__name__}"


def project(root: str) -> dict:
    f = lambda *p: os.path.exists(os.path.join(root, *p))
    out = {"root": os.path.abspath(root),
           "files": {k: f(k) for k in ("app.py", "receipt_parser.py", "ocr_response.py", "eval_baseline.py",
                                       "address_parser.py", "exp_variants.py", "eval_real.py", "eval_rec.py",
                                       "make_train_config.py", "venv/bin/python", "mlflow.db")}}
    if f("app.py"):
        src = open(os.path.join(root, "app.py"), encoding="utf-8").read()
        out["routes"] = re.findall(r"@app\.(get|post|put|delete)\(\"([^\"]+)\"", src)
    if f("receipt_parser.py"):
        rp = open(os.path.join(root, "receipt_parser.py"), encoding="utf-8").read()
        out["parser_markers"] = {"v0r2": "v0r2" in rp, "address_extract": "extract_address" in rp}
    if f("address_parser.py"):
        out["address_parser_v3"] = "AddressPolicy" in open(os.path.join(root, "address_parser.py"), encoding="utf-8").read()
    repo = os.path.join(root, "PaddleOCR")
    out["paddleocr_repo"] = ({"path": repo, "version": _sh(["git", "-C", repo, "describe", "--tags", "--always"])}
                             if os.path.isdir(repo) else None)
    out["pretrained"] = sorted(os.path.basename(p) for p in glob.glob(os.path.join(root, "pretrained", "*.pdparams")))
    ck = sorted(glob.glob(os.path.join(root, "PaddleOCR", "output", "*", "*.pdparams")) +
                glob.glob(os.path.join(root, "output", "*", "*.pdparams")))
    out["checkpoints"] = [os.path.relpath(p, root) for p in ck][:40]
    data = {}
    for d in ("real", "real_holdout", "real_new/dev2", "real_new/holdout2", "synthetic", "rec_data", "det_data"):
        p = os.path.join(root, d)
        if os.path.isdir(p):
            imgs = [x for x in glob.glob(os.path.join(p, "**", "*"), recursive=True)
                    if x.lower().endswith((".jpg", ".jpeg", ".png"))]
            data[d] = {"images": len(imgs), "label_files": sorted(os.path.relpath(x, root) for x in
                                                                  glob.glob(os.path.join(p, "*.txt")) + glob.glob(os.path.join(p, "*.csv")))[:20]}
    out["data"] = data
    out["line_level_real_labels"] = ("없음으로 보임: 실제 영수증 폴더에 줄 단위 라벨(det/rec 형식)이 없음"
                                     if not any("real" in k and any(x.endswith(".txt") for x in v["label_files"])
                                                for k, v in data.items()) else "확인 필요: real 폴더에 txt 라벨 있음")
    return out


def gpu_and_paddle(run_check: bool) -> dict:
    out = {"nvidia_smi": resources.nvidia_smi(resources.physical_gpu_index()),
           "cuda_visible_devices": os.getenv("CUDA_VISIBLE_DEVICES")}
    try:
        import paddle
        p = {"version": paddle.__version__, "compiled_with_cuda": bool(paddle.device.is_compiled_with_cuda())}
        try:
            p["cuda"] = paddle.version.cuda()
            p["cudnn"] = paddle.version.cudnn()
        except Exception:
            pass
        if p["compiled_with_cuda"]:
            n = paddle.device.cuda.device_count()
            p["visible_gpu_count"] = n
            if n:
                try:
                    prop = paddle.device.cuda.get_device_properties(0)
                    p["gpu0"] = {"name": prop.name, "total_mib": round(prop.total_memory / 2**20)}
                except Exception as e:
                    p["gpu0"] = f"속성 조회 실패: {type(e).__name__}"
        if run_check:
            try:
                paddle.utils.run_check()
                p["run_check"] = "통과"
            except Exception as e:
                p["run_check"] = f"실패: {type(e).__name__}: {e}"
        out["paddle"] = p
    except Exception as e:
        out["paddle"] = {"error": f"{type(e).__name__}: {e}"}
    return out


def init_ocr_smoke(device: str) -> dict:
    """운영과 같은 설정으로 초기화 + 빈 이미지 1장 추론. 캐시에 없는 모델이면 다운로드될 수 있어 미리 확인한다."""
    import numpy as np
    from .engine import OcrEngine, model_cache_dir
    from .settings import load_config
    cfg = load_config()
    cfg["ocr"]["device"] = device
    cached = sorted(os.path.basename(p) for p in glob.glob(os.path.join(model_cache_dir(), "*")))
    need = ("PP-OCRv5_server_det", "korean_PP-OCRv5_mobile_rec")
    if not all(n in cached for n in need):
        return {"skipped": f"캐시에 {need} 가 없어 초기화하지 않음 (다운로드 방지)", "cached": cached}
    eng = OcrEngine(cfg["ocr"], False, cfg["expect"]["paddleocr_version"])
    out = eng.run(np.full((320, 240, 3), 255, np.uint8))
    return {"init_s": round(eng.init_s, 2), "kwargs": eng.kwargs, "blank_image_tokens": len(out.tokens),
            "ocr_ms": round(out.ocr_ms, 1), "sync": out.sync, "memory": resources.snapshot(),
            "note": "초기화 로그의 'Creating model' 줄에서 실제 탐지·인식 모델 이름을 확인하세요"}


def run(root: str = ".", run_check: bool = False, init_ocr: bool = False, device: str = "gpu:0") -> dict:
    rep = {
        "host": platform.node(), "os": platform.platform(), "python": sys.version.split()[0],
        "executable": sys.executable,
        "packages": {m: _ver(m) for m in ("paddle", "paddleocr", "paddlex", "cv2", "numpy", "PIL", "yaml", "fastapi",
                                          "psutil", "mlflow")},
        "pip_names": _sh([sys.executable, "-m", "pip", "list", "--format=freeze"], 60),
        "project": project(root),
        "gpu": gpu_and_paddle(run_check),
        "exif": imageio.check_exif_behavior(),
        "model_cache": sorted(os.path.basename(p) for p in glob.glob(os.path.join(
            os.path.expanduser(os.getenv("PADDLE_PDX_CACHE_HOME", "~/.paddlex")), "official_models", "*"))),
        "running": [l for l in _sh(["ps", "-eo", "pid,args"]).splitlines()
                    if re.search(r"uvicorn|app:app|mlflow|tools/train.py", l) and "envcheck" not in l][:20],
        "flow": FLOW,
    }
    keep = ("paddle", "paddleocr", "paddlex", "opencv", "numpy", "fastapi", "uvicorn", "ultralytics", "torch")
    rep["pip_names"] = [l for l in rep["pip_names"].splitlines() if l.lower().startswith(keep)]
    if init_ocr:
        try:
            rep["ocr_smoke"] = init_ocr_smoke(device)
        except Exception as e:
            rep["ocr_smoke"] = {"error": f"{type(e).__name__}: {e}"}
    return rep


def print_report(rep: dict):
    pk = rep["packages"]
    print(f"Python {rep['python']} ({rep['executable']})")
    print(f"paddle {pk['paddle']} | paddleocr {pk['paddleocr']} | paddlex {pk['paddlex']} | opencv {pk['cv2']}")
    g = rep["gpu"]
    d = (g["nvidia_smi"] or {}).get("device")
    if d:
        print(f"GPU {d['index']}: {d['name']} {d['total_mib']:.0f} MiB (사용 중 {d['used_mib']:.0f}), 드라이버 {d['driver']}")
    else:
        print("GPU: nvidia-smi 로 대상 GPU 를 찾지 못함", g["nvidia_smi"].get("reason", ""))
    p = g.get("paddle", {})
    print(f"Paddle CUDA 빌드 {p.get('compiled_with_cuda')} (CUDA {p.get('cuda')}, cuDNN {p.get('cudnn')}), "
          f"보이는 GPU {p.get('visible_gpu_count')}, run_check {p.get('run_check', '안 함')}")
    e = rep["exif"]
    print(f"OpenCV imdecode EXIF 적용: {e['imdecode_applies_exif']} → {e['note']}")
    pr = rep["project"]
    print(f"프로젝트 {pr['root']} | 진입점 {pr.get('routes')} | 주소 v3 적용: {pr.get('address_parser_v3')}")
    print(f"PaddleOCR 저장소: {pr['paddleocr_repo']} | 사전학습 가중치: {pr['pretrained']}")
    print(f"체크포인트 {len(pr['checkpoints'])}개 | 데이터: { {k: v['images'] for k, v in pr['data'].items()} }")
    print(f"실제 영수증 줄 단위 라벨: {pr['line_level_real_labels']}")
    print(f"모델 캐시: {rep['model_cache']}")
    print(f"실행 중: {rep['running']}")
    if "ocr_smoke" in rep:
        print("OCR 초기화 확인:", json.dumps(rep["ocr_smoke"], ensure_ascii=False)[:600])
