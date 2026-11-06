"""이미지 읽기와 전처리. 모든 함수는 BGR, uint8, 3채널 이미지를 받고 돌려준다.

EXIF 방향 처리 (이중 회전 방지)
  - opencv  : app.py 와 같은 cv2.imdecode(IMREAD_COLOR). OpenCV 가 EXIF 를 적용하는지는 빌드마다 다를 수 있어
              env 점검(check_exif_behavior)으로 서버에서 확인한다. 여기서 추가 회전은 하지 않는다.
  - explicit: IMREAD_IGNORE_ORIENTATION 으로 OpenCV 회전을 끄고, EXIF 태그를 직접 한 번만 적용한다.
  - ignore  : 회전하지 않는다.
원본 파일은 읽기만 한다. 저장은 save_image 로 다른 경로에만 한다.
"""
from __future__ import annotations

import io
import os

import cv2
import numpy as np


def read_exif_orientation(raw: bytes) -> int | None:
    try:
        from PIL import Image
        return Image.open(io.BytesIO(raw)).getexif().get(274)
    except Exception:
        return None


def apply_orientation(img: np.ndarray, tag: int | None) -> np.ndarray:
    """EXIF Orientation 1~8 을 표시 방향으로 바꾼다 (PIL ImageOps.exif_transpose 와 같은 결과)."""
    if tag in (None, 1):
        return img
    ops = {
        2: lambda m: cv2.flip(m, 1),
        3: lambda m: cv2.rotate(m, cv2.ROTATE_180),
        4: lambda m: cv2.flip(m, 0),
        5: lambda m: cv2.flip(cv2.rotate(m, cv2.ROTATE_90_CLOCKWISE), 1),
        6: lambda m: cv2.rotate(m, cv2.ROTATE_90_CLOCKWISE),
        7: lambda m: cv2.flip(cv2.rotate(m, cv2.ROTATE_90_COUNTERCLOCKWISE), 1),
        8: lambda m: cv2.rotate(m, cv2.ROTATE_90_COUNTERCLOCKWISE),
    }
    return ops[tag](img) if tag in ops else img


def ensure_bgr_u8(img: np.ndarray) -> np.ndarray:
    if img is None:
        raise ValueError("이미지가 비어 있습니다")
    if img.dtype != np.uint8:
        if img.dtype == np.uint16:
            img = (img / 257).astype(np.uint8)
        else:
            img = np.clip(img, 0, 255).astype(np.uint8)
    if img.ndim == 2:
        img = cv2.cvtColor(img, cv2.COLOR_GRAY2BGR)
    elif img.shape[2] == 4:
        img = cv2.cvtColor(img, cv2.COLOR_BGRA2BGR)
    if img.ndim != 3 or img.shape[2] != 3:
        raise ValueError(f"3채널 이미지가 아닙니다: shape={img.shape}")
    return np.ascontiguousarray(img)


def decode(raw: bytes, exif_mode: str = "opencv") -> tuple[np.ndarray, dict]:
    if not raw:
        raise ValueError("빈 파일입니다")
    buf = np.frombuffer(raw, np.uint8)
    tag = read_exif_orientation(raw)
    if exif_mode == "opencv":
        img = cv2.imdecode(buf, cv2.IMREAD_COLOR)
        applied = "opencv 내부 처리(빌드에 따름)"
    elif exif_mode in ("explicit", "ignore"):
        img = cv2.imdecode(buf, cv2.IMREAD_COLOR | cv2.IMREAD_IGNORE_ORIENTATION)
        if img is not None and exif_mode == "explicit":
            img = apply_orientation(img, tag)
        applied = "직접 1회 적용" if exif_mode == "explicit" else "적용 안 함"
    else:
        raise ValueError(f"exif_mode 는 opencv / explicit / ignore: {exif_mode}")
    if img is None:
        raise ValueError("이미지를 읽을 수 없습니다")
    img = ensure_bgr_u8(img)
    return img, {"exif_orientation": tag, "exif_mode": exif_mode, "exif_applied": applied,
                 "decoded_hw": list(img.shape[:2]), "channels": "BGR", "dtype": str(img.dtype)}


def check_exif_behavior() -> dict:
    """이 서버의 OpenCV 가 imdecode 에서 EXIF 회전을 적용하는지 합성 이미지로 확인 (파일 저장 없음)."""
    from PIL import Image
    im = Image.new("RGB", (40, 20), "white")
    ex = Image.Exif()
    ex[274] = 6
    b = io.BytesIO()
    im.save(b, "JPEG", exif=ex.tobytes())
    raw = b.getvalue()
    a = cv2.imdecode(np.frombuffer(raw, np.uint8), cv2.IMREAD_COLOR)
    e, _ = decode(raw, "explicit")
    applies = a.shape[:2] == (40, 20)
    return {"cv2_version": cv2.__version__, "imdecode_applies_exif": applies,
            "explicit_hw": list(e.shape[:2]), "opencv_hw": list(a.shape[:2]),
            "note": ("opencv 와 explicit 결과 방향이 같음" if a.shape == e.shape
                     else "opencv 모드는 EXIF 를 적용하지 않음 → 휴대폰 세로 사진이 누울 수 있음 (explicit 권장 후보)")}


# ---------- 전처리 ----------

def resize_keep_ratio(img: np.ndarray, max_side=None, min_side=None) -> tuple[np.ndarray, float]:
    h, w = img.shape[:2]
    scale = 1.0
    if max_side and max(h, w) > max_side:
        scale = max_side / max(h, w)
    elif min_side and min(h, w) < min_side:
        scale = min_side / min(h, w)
    if scale == 1.0:
        return img, 1.0
    interp = cv2.INTER_AREA if scale < 1 else cv2.INTER_CUBIC
    out = cv2.resize(img, (max(1, round(w * scale)), max(1, round(h * scale))), interpolation=interp)
    return out, scale


def clahe(img: np.ndarray, clip=2.0, tile=8) -> np.ndarray:
    lab = cv2.cvtColor(img, cv2.COLOR_BGR2LAB)
    l_, a, b = cv2.split(lab)
    l_ = cv2.createCLAHE(clipLimit=float(clip), tileGridSize=(int(tile), int(tile))).apply(l_)
    return cv2.cvtColor(cv2.merge((l_, a, b)), cv2.COLOR_LAB2BGR)


def denoise(img: np.ndarray, h=10, hcolor=10, template=7, search=21) -> np.ndarray:
    return cv2.fastNlMeansDenoisingColored(img, None, h, hcolor, template, search)


def binarize(img: np.ndarray, block=11, c=2) -> np.ndarray:
    g = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    th = cv2.adaptiveThreshold(g, 255, cv2.ADAPTIVE_THRESH_GAUSSIAN_C, cv2.THRESH_BINARY, int(block), int(c))
    return cv2.cvtColor(th, cv2.COLOR_GRAY2BGR)       # OCR 입력은 3채널 BGR 유지


def preprocess(img: np.ndarray, cfg: dict) -> tuple[np.ndarray, dict]:
    """cfg: 설정 전체 (resize, pre 사용). 순서: 크기 → CLAHE → 노이즈 제거 → 이진화"""
    img = ensure_bgr_u8(img)
    steps = []
    r = cfg["resize"]
    img, scale = resize_keep_ratio(img, r.get("max_side"), r.get("min_side"))
    if scale != 1.0:
        steps.append(f"resize x{scale:.4f}")
    p = cfg["pre"]
    if p["clahe"]:
        img = clahe(img, p["clahe_clip"], p["clahe_tile"])
        steps.append("clahe")
    if p["denoise"]:
        img = denoise(img, p["denoise_h"], p["denoise_hcolor"], p["denoise_template"], p["denoise_search"])
        steps.append("denoise")
    if p["binarize"]:
        img = binarize(img, p["binarize_block"], p["binarize_c"])
        steps.append("binarize")
    img = ensure_bgr_u8(img)
    return img, {"steps": steps, "scale": scale, "input_hw": list(img.shape[:2])}


def save_image(img: np.ndarray, out_dir: str, name: str, src_path: str | None = None) -> str:
    """전처리 결과 저장. 원본 경로와 같으면 거부하고, 같은 이름이 있으면 번호를 붙인다."""
    os.makedirs(out_dir, exist_ok=True)
    base, _ = os.path.splitext(os.path.basename(name))
    path = os.path.join(out_dir, base + "_pre.png")
    k = 1
    while os.path.exists(path):
        path = os.path.join(out_dir, f"{base}_pre{k}.png")
        k += 1
    if src_path and os.path.abspath(path) == os.path.abspath(src_path):
        raise ValueError("원본을 덮어쓸 수 없습니다")
    if not cv2.imwrite(path, img):
        raise OSError(f"저장 실패: {path}")
    return path
