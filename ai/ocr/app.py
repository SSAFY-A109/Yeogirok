"""영수증 OCR 서버 (FastAPI + PaddleOCR). OCR 설정은 eval_baseline.run_ocr_factory 를 그대로 사용."""
from __future__ import annotations
import os
import cv2
import numpy as np
from fastapi import FastAPI, File, HTTPException, UploadFile
from eval_baseline import run_ocr_factory
from ocr_response import to_response
from receipt_parser import boxes_from_polys, group_lines, parse_receipt

app = FastAPI(title="Receipt OCR")
_run = None
MAX_BYTES = 10 * 1024 * 1024


def get_runner():
    global _run
    if _run is None:
        _run = run_ocr_factory(os.getenv("OCR_DEVICE", "gpu:0"), textline_orient=False,
                               rec_model_dir=os.getenv("OCR_REC_MODEL_DIR") or None)
    return _run


@app.on_event("startup")
def warmup():
    get_runner()


def decode(raw: bytes) -> np.ndarray:
    try:
        img = cv2.imdecode(np.frombuffer(raw, np.uint8), cv2.IMREAD_COLOR)
    except cv2.error:                             # 디코딩 중 예외도 400 (이전: 500)
        img = None
    if img is None:
        raise HTTPException(400, "이미지를 읽을 수 없습니다.")
    return img


@app.post("/ocr/receipt")
async def ocr_receipt(file: UploadFile = File(...)):
    raw = await file.read()
    if not raw:                                   # 빈 파일(0바이트) → 400 (이전: cv2 예외로 500)
        raise HTTPException(400, "빈 파일입니다.")
    if len(raw) > MAX_BYTES:
        raise HTTPException(413, "10MB 이하 이미지만 받습니다.")
    texts, scores, polys = get_runner()(decode(raw))
    return to_response(parse_receipt(group_lines(boxes_from_polys(texts, scores, polys))))   # rawLines(OCR 원문)는 반환하지 않음


@app.get("/health")
def health():
    return {"ok": True}
