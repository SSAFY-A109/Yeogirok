"""입력 사진 긴 변 축소 테스트 (11/10). OCR 모델을 불러오지 않는다.
실행: python test_app_resize.py   (fastapi, httpx, python-multipart 필요)
"""
import cv2
import numpy as np
from fastapi.testclient import TestClient

import app as server


def test_portrait_phone_photo_to_1280():
    out = server.shrink(np.zeros((4032, 3024, 3), np.uint8))
    assert out.shape[:2] == (1280, 960)


def test_landscape_keeps_ratio():
    out = server.shrink(np.zeros((3024, 4032, 3), np.uint8))
    assert out.shape[:2] == (960, 1280)


def test_small_image_unchanged():
    img = np.zeros((800, 600, 3), np.uint8)
    assert server.shrink(img) is img


def test_zero_disables():
    old = server.OCR_MAX_SIDE
    server.OCR_MAX_SIDE = 0
    try:
        img = np.zeros((4032, 3024, 3), np.uint8)
        assert server.shrink(img) is img
    finally:
        server.OCR_MAX_SIDE = old


def test_api_passes_shrunk_image_to_ocr():
    seen = {}

    def fake(img):
        seen.setdefault("hw", img.shape[:2])   # 방향 보정으로 다시 읽어도 첫 입력 크기만 본다
        return [], [], []
    old = server.get_runner
    server.get_runner = lambda: fake
    try:
        ok, buf = cv2.imencode(".jpg", np.full((4032, 3024, 3), 255, np.uint8))
        r = TestClient(server.app).post("/ocr/receipt", files={"file": ("r.jpg", buf.tobytes(), "image/jpeg")})
    finally:
        server.get_runner = old
    assert r.status_code == 200
    assert seen["hw"] == (1280, 960)
    assert tuple(r.json()) == ("status", "placeName", "date", "time", "address", "addressComplete")   # S15P31A109-95


if __name__ == "__main__":
    tests = [v for k, v in dict(globals()).items() if k.startswith("test_")]
    for t in tests:
        t()
        print("PASS", t.__name__)
    print(f"{len(tests)}/{len(tests)} 통과")
