"""실험 설정: 현재 API 설정(baseline)을 기본값으로 두고, 비교 모드는 한 요소만 바꾼다.

새 전처리·방향 모델은 모두 기본 꺼짐. YAML 파일(--config)과 --set key=value 로 바꿀 수 있다.
"""
from __future__ import annotations

import copy
import json
import os

# 현재 운영 API (app.py → eval_baseline.run_ocr_factory) 와 같은 값
DEFAULTS = {
    "decode": {
        "exif_mode": "opencv",      # opencv: app.py 와 같은 cv2.imdecode(IMREAD_COLOR) (EXIF 적용 여부는 OpenCV 빌드에 따름 → env 점검에서 확인)
                                    # explicit: OpenCV 회전을 끄고(IMREAD_IGNORE_ORIENTATION) EXIF 태그를 직접 한 번만 적용
                                    # ignore: EXIF 무시
    },
    "resize": {"max_side": None, "min_side": None},   # 비율 유지. None = 원본 크기 (운영과 같음)
    "pre": {
        "clahe": False, "clahe_clip": 2.0, "clahe_tile": 8,
        "denoise": False, "denoise_h": 10, "denoise_hcolor": 10, "denoise_template": 7, "denoise_search": 21,
        "binarize": False, "binarize_block": 11, "binarize_c": 2,
    },
    "ocr": {
        "lang": "korean",
        "device": "gpu:0",
        "use_doc_orientation_classify": False,   # PaddleOCR 3.x 문서 방향 분류 (0/90/180/270)
        "use_doc_unwarping": False,              # PaddleOCR 3.x 문서 펴기
        "use_textline_orientation": False,       # PaddleOCR 3.x 줄 방향 분류
        "text_det_thresh": 0.2,
        "text_det_box_thresh": 0.4,
        "text_detection_model_name": None,
        "text_recognition_model_name": None,
        "text_recognition_model_dir": None,      # 파인튜닝한 인식 모델 폴더 (설정하면 모델 이름 두 개도 명시해야 함)
    },
    "fields": {
        "datetime_ambiguity": "legacy",   # [미합의] legacy: 기존 규칙대로 선택 / null_if_ambiguous: 근거 없이 후보가 여럿이면 null
        "date_validation": "legacy",      # [미합의] legacy: 월 1~12·일 1~31 / calendar: 실제 달력 날짜만 (2/30 제외)
        "date_lookalike_fix": "legacy",   # [실험 11/04] legacy: 운영과 같음 / fallback: 날짜를 못 찾았을 때만 날짜 모양 안의
                                          #   혼동 문자(O·o·C·c·D→0, I·l·|→1)를 숫자로 바꿔 한 번 더 찾는다 (fields.py)
    },
    "address": {
        "return_level": "road",           # [확정 v3]
        "sido_style": "as_printed",       # [미합의]
        "include_eupmyeon": True,         # [미합의]
        "complete_requires_sido": True,   # [미합의]
        "allow_no_sido": True,            # [미합의]
        "join_next_line": True,           # [미합의]
    },
    "expect": {"paddleocr_version": "3.7.0"},   # 서버 기록(10/28 패키지 lock). 다르면 경고만 남긴다
}

# 비교 모드: baseline 대비 한 요소만 바꾼다
MODES = {
    "baseline": {},
    "clahe": {"pre.clahe": True},
    "denoise": {"pre.denoise": True},
    "binarize": {"pre.binarize": True},
    "docori": {"ocr.use_doc_orientation_classify": True},
    "textline": {"ocr.use_textline_orientation": True},
    "unwarp": {"ocr.use_doc_unwarping": True},
    "exif_explicit": {"decode.exif_mode": "explicit"},
}

CHOICES = {
    "decode.exif_mode": ("opencv", "explicit", "ignore"),
    "fields.datetime_ambiguity": ("legacy", "null_if_ambiguous"),
    "fields.date_validation": ("legacy", "calendar"),
    "fields.date_lookalike_fix": ("legacy", "fallback"),
    "address.return_level": ("road", "building"),
    "address.sido_style": ("as_printed", "full"),
}


def _parse_value(v: str):
    low = v.strip().lower()
    if low in ("true", "false"):
        return low == "true"
    if low in ("none", "null", ""):
        return None
    try:
        return json.loads(v)
    except ValueError:
        return v


def set_key(cfg: dict, dotted: str, value):
    sect, _, key = dotted.partition(".")
    if sect not in cfg or not key or key not in cfg[sect]:
        raise KeyError(f"알 수 없는 설정 키: {dotted}  (가능: {', '.join(f'{s}.{k}' for s in cfg for k in cfg[s])})")
    if dotted in CHOICES and value not in CHOICES[dotted]:
        raise ValueError(f"{dotted} 값은 {CHOICES[dotted]} 중 하나: {value}")
    cfg[sect][key] = value


def load_config(path: str | None = None, mode: str = "baseline", sets: list[str] | None = None) -> dict:
    cfg = copy.deepcopy(DEFAULTS)
    if path:
        if not os.path.exists(path):
            raise FileNotFoundError(f"설정 파일이 없습니다: {path}")
        import yaml
        data = yaml.safe_load(open(path, encoding="utf-8")) or {}
        for sect, vals in data.items():
            for k, v in (vals or {}).items():
                set_key(cfg, f"{sect}.{k}", v)
    if mode not in MODES:
        raise KeyError(f"알 수 없는 모드: {mode} (가능: {', '.join(MODES)})")
    for k, v in MODES[mode].items():
        set_key(cfg, k, v)
    for s in sets or []:
        if "=" not in s:
            raise ValueError(f"--set 은 key=value 형식: {s}")
        k, v = s.split("=", 1)
        set_key(cfg, k.strip(), _parse_value(v))
    cfg["_mode"] = mode
    cfg["_changed"] = diff(DEFAULTS, cfg)
    return cfg


def diff(a: dict, b: dict) -> dict:
    out = {}
    for sect in a:
        for k in a[sect]:
            if a[sect][k] != b[sect][k]:
                out[f"{sect}.{k}"] = b[sect][k]
    return out


def address_policy(cfg: dict):
    from address_parser import AddressPolicy
    return AddressPolicy(**cfg["address"])
