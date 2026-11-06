"""PaddleOCR 3.x 실행 래퍼. 출력은 공통 내부 구조(OcrToken: 텍스트·좌표·신뢰도)로 바꾼다.

- PaddleOCR 3.x 의 PaddleOCR(...) 생성자 파라미터 이름만 쓴다 (2.x 의 ocr.ocr(), use_angle_cls 등은 쓰지 않음)
- baseline 설정은 운영 API(eval_baseline.run_ocr_factory)와 같은 인자를 만든다 (tests/test_engine.py 에서 확인)
- 방향 분류·문서 펴기 모델이 캐시에 없으면 다운로드하지 않고 멈춘다 (--allow-download 로만 허용)
"""
from __future__ import annotations

import glob
import os
import time
from dataclasses import dataclass, field

OPTIONAL_MODELS = {   # 설정 키 → 캐시 폴더 이름에 들어가는 문자열 (PaddleX official_models)
    "use_doc_orientation_classify": "doc_ori",
    "use_doc_unwarping": "UVDoc",
    "use_textline_orientation": "textline_ori",
}


@dataclass
class OcrToken:
    text: str
    score: float
    poly: list            # [[x, y], ...] 전처리 후 이미지 좌표 (scale 은 meta 에 기록)


@dataclass
class OcrOutput:
    tokens: list
    ocr_ms: float
    sync: str | None
    doc_angle: int | None = None
    extra: dict = field(default_factory=dict)

    def as_triplet(self):
        return ([t.text for t in self.tokens], [t.score for t in self.tokens], [t.poly for t in self.tokens])


def build_kwargs(ocr: dict) -> dict:
    kw = {k: ocr[k] for k in ("device", "use_doc_orientation_classify", "use_doc_unwarping",
                              "use_textline_orientation", "text_det_thresh", "text_det_box_thresh")}
    det, rec, rec_dir = ocr["text_detection_model_name"], ocr["text_recognition_model_name"], ocr["text_recognition_model_dir"]
    if rec_dir:
        if not (det and rec):
            raise ValueError("text_recognition_model_dir 를 쓰면 text_detection_model_name 과 text_recognition_model_name 도 "
                             "정해야 합니다 (운영과 같게: PP-OCRv5_server_det, korean_PP-OCRv5_mobile_rec)")
        if not os.path.isdir(os.path.expanduser(rec_dir)):
            raise FileNotFoundError(f"인식 모델 폴더가 없습니다: {rec_dir}")
        kw.update(text_detection_model_name=det, text_recognition_model_name=rec,
                  text_recognition_model_dir=os.path.expanduser(rec_dir))
    elif det or rec:
        if det:
            kw["text_detection_model_name"] = det
        if rec:
            kw["text_recognition_model_name"] = rec
    else:
        kw["lang"] = ocr["lang"]
    return kw


def model_cache_dir() -> str:
    return os.path.join(os.path.expanduser(os.getenv("PADDLE_PDX_CACHE_HOME", "~/.paddlex")), "official_models")


def check_optional_models(ocr: dict, allow_download: bool) -> list[str]:
    need = [k for k in OPTIONAL_MODELS if ocr.get(k)]
    if not need:
        return []
    cached = [os.path.basename(p) for p in glob.glob(os.path.join(model_cache_dir(), "*"))]
    missing = [k for k in need if not any(OPTIONAL_MODELS[k] in c for c in cached)]
    if missing and not allow_download:
        raise RuntimeError(f"{missing} 에 필요한 모델이 캐시({model_cache_dir()})에 없어 멈춥니다. "
                           "다운로드가 일어날 수 있으므로 기본으로 막아 두었습니다. 허용하려면 --allow-download")
    return [c for c in cached if any(OPTIONAL_MODELS[k] in c for k in need)]


def paddleocr_version() -> str:
    import paddleocr
    return getattr(paddleocr, "__version__", "unknown")


def gpu_sync(device: str) -> str | None:
    """GPU 비동기 실행 때문에 시간이 짧게 재지지 않도록 동기화. 쓴 방법을 돌려준다."""
    if not device.startswith("gpu"):
        return None
    try:
        import paddle
        if hasattr(paddle.device, "synchronize"):
            paddle.device.synchronize()
            return "paddle.device.synchronize"
        if hasattr(paddle.device, "cuda") and hasattr(paddle.device.cuda, "synchronize"):
            paddle.device.cuda.synchronize()
            return "paddle.device.cuda.synchronize"
    except ImportError:
        return "paddle 없음 (동기화 안 함)"
    except Exception as e:   # 동기화 실패는 기록만
        return f"실패: {type(e).__name__}"
    return None


class OcrEngine:
    def __init__(self, ocr_cfg: dict, allow_download: bool = False, expect_version: str | None = None):
        ver = paddleocr_version()
        if not ver.startswith("3."):
            raise RuntimeError(f"PaddleOCR 3.x 용 코드입니다. 설치된 버전: {ver}")
        self.version = ver
        self.version_warning = (f"설치 {ver} ≠ 기록 {expect_version}" if expect_version and ver != expect_version else None)
        self.optional_models = check_optional_models(ocr_cfg, allow_download)
        self.kwargs = build_kwargs(ocr_cfg)
        self.device = ocr_cfg["device"]
        from paddleocr import PaddleOCR
        t = time.perf_counter()
        self.ocr = PaddleOCR(**self.kwargs)
        self.init_s = time.perf_counter() - t

    def run(self, img) -> OcrOutput:
        t = time.perf_counter()
        res = self.ocr.predict(img)[0]
        sync = gpu_sync(self.device)
        ms = (time.perf_counter() - t) * 1000
        return convert_result(res, ms, sync)


def convert_result(res, ms: float = 0.0, sync: str | None = None) -> OcrOutput:
    """PaddleOCR 3.x predict() 결과 1개 → OcrOutput"""
    get = res.get if hasattr(res, "get") else (lambda k, d=None: res[k] if k in res else d)
    texts = list(get("rec_texts") or [])
    scores = [float(s) for s in (get("rec_scores") if get("rec_scores") is not None else [])]
    polys = [p.tolist() if hasattr(p, "tolist") else p for p in (get("rec_polys") if get("rec_polys") is not None else [])]
    if not (len(texts) == len(scores) == len(polys)):
        raise ValueError(f"OCR 결과 길이가 맞지 않음: texts {len(texts)}, scores {len(scores)}, polys {len(polys)}")
    toks = [OcrToken(str(t), s, [[float(x), float(y)] for x, y in p]) for t, s, p in zip(texts, scores, polys)]
    dp = get("doc_preprocessor_res")
    angle = None
    if dp is not None:
        dget = dp.get if hasattr(dp, "get") else (lambda k, d=None: d)
        a = dget("angle")
        angle = None if a is None else int(a)
    return OcrOutput(toks, ms, sync, angle)


def to_lines(out: OcrOutput):
    """기존 receipt_parser 의 줄 묶기를 그대로 쓴다."""
    from receipt_parser import boxes_from_polys, group_lines
    return group_lines(boxes_from_polys(*out.as_triplet()))
