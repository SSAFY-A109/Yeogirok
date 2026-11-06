"""PaddleOCR 래퍼: 운영과 같은 인자, 결과 변환 (가짜 paddleocr 모듈 사용, GPU 불필요)."""
import sys
import types

import numpy as np

from ocrlab import engine
from ocrlab.settings import load_config


def _fake_paddleocr(captured):
    m = types.ModuleType("paddleocr")
    m.__version__ = "3.7.0"

    class PaddleOCR:
        def __init__(self, **kw):
            captured.append(kw)

        def predict(self, img):
            return [{"rec_texts": ["가나식당", "2026-10-20 12:30"], "rec_scores": [0.99, np.float32(0.9)],
                     "rec_polys": [np.array([[0, 0], [100, 0], [100, 20], [0, 20]]),
                                   np.array([[0, 40], [100, 40], [100, 60], [0, 60]])],
                     "doc_preprocessor_res": {"angle": -1}}]
    m.PaddleOCR = PaddleOCR
    return m


def test_baseline_kwargs_equal_api():
    cap = []
    old = sys.modules.get("paddleocr")
    sys.modules["paddleocr"] = _fake_paddleocr(cap)
    try:
        import eval_baseline
        eval_baseline.run_ocr_factory("gpu:0", textline_orient=False, rec_model_dir=None)
        assert engine.build_kwargs(load_config()["ocr"]) == cap[-1]          # 운영 app.py 와 같은 인자
        e = engine.OcrEngine(load_config(sets=["ocr.device=cpu"])["ocr"])
        out = e.run(np.zeros((10, 10, 3), np.uint8))
        assert [t.text for t in out.tokens] == ["가나식당", "2026-10-20 12:30"] and out.doc_angle == -1
        assert isinstance(out.tokens[1].score, float) and out.tokens[0].poly[2] == [100.0, 20.0]
        lines = engine.to_lines(out)
        assert [t for t, _ in lines] == ["가나식당", "2026-10-20 12:30"]
    finally:
        if old is None:
            sys.modules.pop("paddleocr", None)
        else:
            sys.modules["paddleocr"] = old


def test_rec_model_dir_needs_model_names():
    c = load_config(sets=["ocr.text_recognition_model_dir=/nonexistent"])["ocr"]
    try:
        engine.build_kwargs(c)
    except ValueError:
        return
    raise AssertionError("모델 이름 없이 통과")


def test_convert_empty_and_mismatch():
    assert engine.convert_result({"rec_texts": [], "rec_scores": [], "rec_polys": []}).tokens == []
    try:
        engine.convert_result({"rec_texts": ["a"], "rec_scores": [], "rec_polys": []})
    except ValueError:
        return
    raise AssertionError("길이 불일치 통과")


def test_optional_model_guard(tmp=None):
    import os
    import tempfile
    d = tempfile.mkdtemp()
    os.environ["PADDLE_PDX_CACHE_HOME"] = d
    try:
        c = load_config(mode="docori")["ocr"]
        try:
            engine.check_optional_models(c, allow_download=False)
            raise AssertionError("캐시 없는데 통과")
        except RuntimeError:
            pass
        os.makedirs(os.path.join(d, "official_models", "PP-LCNet_x1_0_doc_ori"))
        assert engine.check_optional_models(c, False) == ["PP-LCNet_x1_0_doc_ori"]
        assert engine.check_optional_models(load_config()["ocr"], False) == []
    finally:
        os.environ.pop("PADDLE_PDX_CACHE_HOME")


def test_version_guard():
    cap = []
    old = sys.modules.get("paddleocr")
    fake = _fake_paddleocr(cap)
    fake.__version__ = "2.10.0"
    sys.modules["paddleocr"] = fake
    try:
        engine.OcrEngine(load_config()["ocr"])
        raise AssertionError("2.x 통과")
    except RuntimeError:
        pass
    finally:
        if old is None:
            sys.modules.pop("paddleocr", None)
        else:
            sys.modules["paddleocr"] = old
