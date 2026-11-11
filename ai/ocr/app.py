"""영수증 OCR 서버 (FastAPI + PaddleOCR). OCR 설정은 eval_baseline.run_ocr_factory 를 그대로 사용."""
from __future__ import annotations
import logging
import os
import cv2
import numpy as np
from fastapi import FastAPI, File, HTTPException, UploadFile
from eval_baseline import run_ocr_factory
from ocr_response import to_response
from receipt_parser import boxes_from_polys, group_lines, parse_receipt
from final_rules import apply_final_rules
from orient_retry import read_with_orientation

app = FastAPI(title="Receipt OCR")
_run = None
MAX_BYTES = 10 * 1024 * 1024
OCR_MAX_SIDE = int(os.getenv("OCR_MAX_SIDE", "1280"))   # 사진 긴 변 상한(px). 0 = 줄이지 않음
ORIENT_MODE = os.getenv("OCR_ORIENT_RETRY", "fast")   # 방향 보정: fast(기본) | auto | off
DEFAULT_REC_MODEL_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "models", "rec_b30_v1")   # 최종 인식 모델 B30
log = logging.getLogger("uvicorn.error")


def rec_model_dir():
    """인식 모델 폴더. 기본은 B30(models/rec_b30_v1). OCR_REC_MODEL_DIR=official 이면 공식 가중치(되돌리기용).
    폴더가 없으면 오류로 멈춘다 (모델 파일이 빠진 채 조용히 공식 모델로 돌지 않게)."""
    v = os.getenv("OCR_REC_MODEL_DIR", "").strip()
    if v.lower() == "official":
        return None
    path = v or DEFAULT_REC_MODEL_DIR
    if not os.path.isdir(path):
        raise RuntimeError(f"인식 모델 폴더가 없습니다: {path} (공식 모델로 되돌리려면 OCR_REC_MODEL_DIR=official)")
    return path


def get_runner():
    global _run
    if _run is None:
        _run = run_ocr_factory(os.getenv("OCR_DEVICE", "gpu:0"), textline_orient=False,
                               rec_model_dir=rec_model_dir())
        log.info("OCR 인식 모델: %s / 방향 보정: %s", rec_model_dir() or "official", ORIENT_MODE)
    return _run


@app.on_event("startup")
def warmup():
    get_runner()


def shrink(img: np.ndarray) -> np.ndarray:
    """긴 변이 OCR_MAX_SIDE 보다 크면 비율을 유지해 줄인다 (평가 도구 ocr_v3 의 resize.max_side 와 같은 계산).
    휴대폰 원본(예: 3024×4032)을 그대로 OCR 하면 메모리·시간이 크게 늘고, 평가에서도 1280 이 원본·1600 보다 정확도가 같거나 높았다."""
    h, w = img.shape[:2]
    if not OCR_MAX_SIDE or max(h, w) <= OCR_MAX_SIDE:
        return img
    s = OCR_MAX_SIDE / max(h, w)
    return cv2.resize(img, (max(1, round(w * s)), max(1, round(h * s))), interpolation=cv2.INTER_AREA)


def decode(raw: bytes) -> np.ndarray:
    try:
        img = cv2.imdecode(np.frombuffer(raw, np.uint8), cv2.IMREAD_COLOR)
    except cv2.error:                             # 디코딩 중 예외도 400 (이전: 500)
        img = None
    if img is None:
        raise HTTPException(400, "이미지를 읽을 수 없습니다.")
    return shrink(img)                            # 긴 변 OCR_MAX_SIDE(기본 1280)로 축소


@app.post("/ocr/receipt")
async def ocr_receipt(file: UploadFile = File(...)):
    raw = await file.read()
    if not raw:                                   # 빈 파일(0바이트) → 400 (이전: cv2 예외로 500)
        raise HTTPException(400, "빈 파일입니다.")
    if len(raw) > MAX_BYTES:
        raise HTTPException(413, "10MB 이하 이미지만 받습니다.")
    img = decode(raw)                             # 이미지 오류(400)는 OCR 전에 먼저 걸러냄
    try:
        texts, scores, polys, _ = read_with_orientation(get_runner(), img, ORIENT_MODE)   # 깨져 보이면 돌려서 다시 읽기
        lines = group_lines(boxes_from_polys(texts, scores, polys))
        return to_response(apply_final_rules(parse_receipt(lines).to_dict(), lines))   # rawLines(OCR 원문)는 반환하지 않음
    except Exception:
        # 로그에는 예외 종류·트레이스백만 남기고, 응답에는 내부 정보를 넣지 않음
        log.exception("OCR 처리 실패")
        raise HTTPException(500, "OCR 처리 중 오류가 발생했습니다.")


@app.get("/health")
def health():
    return {"ok": True}
