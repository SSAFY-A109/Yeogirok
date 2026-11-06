from ocrlab.settings import DEFAULTS, MODES, load_config


def test_modes_change_one_thing():
    for m in MODES:
        c = load_config(mode=m)
        assert len(c["_changed"]) == (0 if m == "baseline" else 1), (m, c["_changed"])


def test_baseline_equals_current_api_values():
    c = load_config()
    assert c["ocr"]["text_det_thresh"] == 0.2 and c["ocr"]["text_det_box_thresh"] == 0.4
    assert not any(c["pre"][k] for k in ("clahe", "denoise", "binarize"))
    assert not any(c["ocr"][k] for k in ("use_doc_orientation_classify", "use_doc_unwarping", "use_textline_orientation"))
    assert c["decode"]["exif_mode"] == "opencv" and c["address"]["return_level"] == "road"


def test_set_and_errors():
    c = load_config(sets=["ocr.text_det_thresh=0.3", "resize.max_side=1600"])
    assert c["_changed"] == {"ocr.text_det_thresh": 0.3, "resize.max_side": 1600}
    for bad in (["ocr.nope=1"], ["decode.exif_mode=sideways"], ["novalue"]):
        try:
            load_config(sets=bad)
        except (KeyError, ValueError):
            continue
        raise AssertionError(bad)
    assert DEFAULTS["pre"]["clahe"] is False      # 원본 기본값이 바뀌지 않음
