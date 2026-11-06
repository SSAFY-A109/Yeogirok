"""이미지 읽기·EXIF·전처리 (GPU 불필요). 합성 이미지."""
import io
import os
import tempfile

import cv2
import numpy as np
from PIL import Image, ImageOps

from ocrlab import imageio
from ocrlab.settings import load_config


def _jpeg_with_tag(tag, w=60, h=30):
    im = Image.new("RGB", (w, h), "white")
    im.paste((255, 0, 0), (0, 0, w // 3, h))          # 왼쪽 빨간 띠로 방향 구분
    ex = Image.Exif()
    ex[274] = tag
    b = io.BytesIO()
    im.save(b, "JPEG", exif=ex.tobytes(), quality=95)
    return b.getvalue()


def test_explicit_exif_matches_pil_for_all_tags():
    for tag in range(1, 9):
        raw = _jpeg_with_tag(tag)
        ours, meta = imageio.decode(raw, "explicit")
        ref = cv2.cvtColor(np.array(ImageOps.exif_transpose(Image.open(io.BytesIO(raw)))), cv2.COLOR_RGB2BGR)
        assert ours.shape == ref.shape, tag
        assert np.abs(ours.astype(int) - ref.astype(int)).mean() < 3, tag
        assert meta["exif_orientation"] == tag and meta["channels"] == "BGR" and meta["dtype"] == "uint8"


def test_no_double_rotation():
    raw = _jpeg_with_tag(6)
    a, _ = imageio.decode(raw, "opencv")
    e, _ = imageio.decode(raw, "explicit")
    i, _ = imageio.decode(raw, "ignore")
    assert e.shape[:2] == (60, 30) and i.shape[:2] == (30, 60)
    info = imageio.check_exif_behavior()
    if info["imdecode_applies_exif"]:            # 이 OpenCV 는 imdecode 에서 EXIF 를 적용 → opencv == explicit
        assert a.shape == e.shape
    else:
        assert a.shape == i.shape


def test_bad_input():
    for raw in (b"", b"not an image"):
        try:
            imageio.decode(raw)
        except ValueError:
            continue
        raise AssertionError(raw)


def test_ensure_bgr_u8():
    g = np.zeros((5, 7), np.uint8)
    assert imageio.ensure_bgr_u8(g).shape == (5, 7, 3)
    assert imageio.ensure_bgr_u8(np.zeros((5, 7, 4), np.uint8)).shape == (5, 7, 3)
    assert imageio.ensure_bgr_u8(np.full((5, 7, 3), 65535, np.uint16)).max() == 255


def test_resize_keeps_ratio():
    img = np.zeros((3000, 4000, 3), np.uint8)
    out, s = imageio.resize_keep_ratio(img, max_side=1600)
    assert out.shape[:2] == (1200, 1600) and abs(s - 0.4) < 1e-9
    out2, s2 = imageio.resize_keep_ratio(np.zeros((20, 40, 3), np.uint8), min_side=40)
    assert out2.shape[:2] == (40, 80) and s2 == 2
    assert imageio.resize_keep_ratio(img)[1] == 1.0


def test_each_preprocess_keeps_format():
    rng = np.random.default_rng(0)
    img = rng.integers(0, 255, (64, 48, 3), dtype=np.uint8)
    for mode, step in (("clahe", "clahe"), ("denoise", "denoise"), ("binarize", "binarize")):
        out, meta = imageio.preprocess(img, load_config(mode=mode))
        assert out.shape == img.shape and out.dtype == np.uint8 and meta["steps"] == [step]
    base, meta = imageio.preprocess(img, load_config())
    assert meta["steps"] == [] and np.array_equal(base, img)
    b, _ = imageio.preprocess(img, load_config(mode="binarize"))
    assert set(np.unique(b)) <= {0, 255}


def test_save_never_overwrites_source():
    d = tempfile.mkdtemp()
    src = os.path.join(d, "a.png")
    cv2.imwrite(src, np.zeros((4, 4, 3), np.uint8))
    p1 = imageio.save_image(np.ones((4, 4, 3), np.uint8), d, "a.png", src)
    p2 = imageio.save_image(np.ones((4, 4, 3), np.uint8), d, "a.png", src)
    assert p1 != src and p2 != p1 and cv2.imread(src).max() == 0
