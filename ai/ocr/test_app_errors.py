"""API 오류 응답 테스트 (S15P31A109-69). OCR 모델을 불러오지 않는다 (fastapi, httpx, python-multipart 필요)."""
from fastapi.testclient import TestClient

import app as server

client = TestClient(server.app)          # with 문 없이 → startup(모델 로딩) 안 함


def _ocr_must_not_run(img):
    raise AssertionError("오류 요청이 OCR 단계까지 가면 안 됨")


# app.py 에서는 get_runner() 가 decode() 보다 먼저 호출되므로, 모델 대신 가짜 실행기를 넣는다.
server.get_runner = lambda: _ocr_must_not_run


def post(data: bytes, name="r.jpg", field="file"):
    return client.post("/ocr/receipt", files={field: (name, data, "image/jpeg")})


def test_empty_file_is_400():
    r = post(b"")
    assert r.status_code == 400
    assert r.json() == {"detail": "빈 파일입니다."}


def test_not_image_is_400():
    r = post(b"this is not an image")
    assert r.status_code == 400
    assert r.json() == {"detail": "이미지를 읽을 수 없습니다."}


def test_too_large_is_413():
    r = post(b"\0" * (server.MAX_BYTES + 1))
    assert r.status_code == 413


def test_missing_file_field_is_422():
    r = client.post("/ocr/receipt")
    assert r.status_code == 422


def test_errors_are_json():
    for r in (post(b""), post(b"xx")):
        assert r.headers["content-type"].startswith("application/json")


if __name__ == "__main__":
    tests = [v for k, v in dict(globals()).items() if k.startswith("test_")]
    for t in tests:
        t()
        print("PASS", t.__name__)
    print(f"{len(tests)}/{len(tests)} 통과")
