"""PaddleOCR 공식 학습 도구(tools/train.py, eval.py, export_model.py) 실행 준비.

- 공식 설정 파일을 그대로 쓰고, 바꿀 값만 -o 로 덮어쓴다 (설정 파일 복사본을 고치지 않음)
- 학습 가능한 설정은 아래 ALLOWED 두 개뿐이다. 다른 설정은 거부한다
    det: configs/det/PP-OCRv5/PP-OCRv5_server_det.yml   (운영 검출 모델. PaddleOCR v3.7.0 문서에 학습·평가·내보내기 명령 있음)
    rec: configs/rec/PP-OCRv5/multi_language/korean_PP-OCRv5_mobile_rec.yml  (운영 인식 모델. 10/28 서버 학습 기록 rec_v1)
- 기본은 명령을 출력만 한다. --run 을 붙여야 실행한다 (학습은 오래 걸림)
- 데이터의 export_meta.json 에 auto_candidate 가 있거나 없으면 멈춘다 (검수 전 후보로 학습 금지)
"""
from __future__ import annotations

import glob
import json
import os
import re
import shlex
import subprocess
import sys
import time

ALLOWED = {
    "det": ("configs/det/PP-OCRv5/PP-OCRv5_server_det.yml",),
    "rec": ("configs/rec/PP-OCRv5/multi_language/korean_PP-OCRv5_mobile_rec.yml",),
}
REQUIRED = ("task", "paddleocr_repo", "base_config", "pretrained_model", "data_dir", "train_list", "val_list",
            "save_model_dir", "epochs", "learning_rate", "batch_size", "gpu")


class SetupError(Exception):
    pass


def load_settings(path: str) -> dict:
    import yaml
    if not os.path.exists(path):
        raise SetupError(f"학습 설정 파일이 없습니다: {path}")
    s = yaml.safe_load(open(path, encoding="utf-8")) or {}
    miss = [k for k in REQUIRED if s.get(k) in (None, "", [])]
    if miss:
        raise SetupError(f"{path}: 값이 비어 있는 항목 {miss}")
    if s["task"] not in ALLOWED:
        raise SetupError(f"task 는 det 또는 rec: {s['task']}")
    if s["base_config"] not in ALLOWED[s["task"]]:
        raise SetupError(f"이 프로젝트에서 확인된 학습 설정이 아닙니다: {s['base_config']} (허용: {ALLOWED[s['task']]})")
    base = os.path.dirname(os.path.abspath(path))
    for k in ("paddleocr_repo", "data_dir", "label_dir", "save_model_dir", "pretrained_model", "inference_reference"):
        if s.get(k):
            s[k] = os.path.expanduser(s[k])
    s["_settings_dir"] = base
    return s


def _abs(p):
    return os.path.abspath(os.path.expanduser(p))


def check(s: dict, for_action: str = "train") -> dict:
    """실행 전에 경로·데이터·버전·사전을 확인한다. 문제가 있으면 problems 에 담는다."""
    rep = {"problems": [], "notes": []}
    repo = _abs(s["paddleocr_repo"])
    for f in ("tools/train.py", "tools/eval.py", "tools/export_model.py", s["base_config"]):
        if not os.path.exists(os.path.join(repo, f)):
            rep["problems"].append(f"PaddleOCR 저장소에 없음: {os.path.join(repo, f)}")
    try:
        tag = subprocess.run(["git", "-C", repo, "describe", "--tags", "--always"], capture_output=True, text=True,
                             timeout=10).stdout.strip()
        rep["repo_version"] = tag or "알 수 없음"
        import paddleocr
        inst = getattr(paddleocr, "__version__", "?")
        rep["installed_paddleocr"] = inst
        if tag and inst not in tag:
            rep["notes"].append(f"저장소 버전({tag})과 설치된 paddleocr({inst})가 다를 수 있음 → 학습 도구와 추론 패키지 버전을 맞추세요")
    except Exception as e:
        rep["notes"].append(f"버전 확인 생략: {type(e).__name__}")
    if for_action == "train" and not os.path.exists(_abs(s["pretrained_model"])):
        rep["problems"].append(f"사전학습 가중치 없음: {s['pretrained_model']} (자동 다운로드하지 않음. 공식 문서의 링크로 직접 받기)")
    data = _abs(s["data_dir"])
    ldir = _abs(s.get("label_dir") or s["data_dir"])      # det: 이미지는 data_dir, 라벨 목록은 label_dir
    if not os.path.isdir(data):
        rep["problems"].append(f"data_dir 없음: {data}")
    lists = (s["train_list"] + s["val_list"]) if for_action == "train" else s["val_list"]
    for lf in lists:
        p = os.path.join(ldir, lf)
        if not os.path.exists(p):
            rep["problems"].append(f"라벨 목록 없음: {p}")
        elif sum(1 for l in open(p, encoding="utf-8") if "\t" in l) == 0:
            rep["problems"].append(f"라벨 목록이 비어 있음: {p}")
    meta_p = os.path.join(ldir, "export_meta.json")
    if os.path.exists(meta_p):
        meta = json.load(open(meta_p, encoding="utf-8"))
        rep["data_sources"] = meta.get("sources")
        if "auto_candidate" in (meta.get("sources") or []):
            rep["problems"].append("데이터에 auto_candidate(검수 전 OCR 후보)가 포함됨 → 학습 금지")
        if meta.get("task") and meta["task"] != s["task"]:
            rep["problems"].append(f"데이터 형식({meta['task']})과 task({s['task']})가 다름")
        if meta.get("data_dir") and os.path.abspath(meta["data_dir"]) != data:
            rep["problems"].append(f"data_dir({data})가 내보낼 때 기준 폴더({meta['data_dir']})와 다름 → 이미지 경로가 안 맞음")
    else:
        rep["notes"].append(f"{meta_p} 없음: 데이터 출처(사람 검수 여부)를 확인할 수 없음. 기존 합성·AI Hub 데이터라면 출처를 직접 확인하세요")
    if s["task"] == "rec" and os.path.exists(os.path.join(repo, s["base_config"])):
        from . import dictcheck
        import yaml
        cfg = yaml.safe_load(open(os.path.join(repo, s["base_config"]), encoding="utf-8"))
        g = cfg["Global"]
        dict_path = os.path.join(repo, g["character_dict_path"])
        try:
            d = dictcheck.run(dict_path, s.get("inference_reference"),
                              _abs(s["pretrained_model"]) if for_action == "train" else None,
                              bool(g.get("use_space_char", False)),
                              [os.path.join(ldir, lf) for lf in lists if os.path.exists(os.path.join(ldir, lf))])
            rep["dict"] = {k: v for k, v in d.items() if k != "pretrained_heads"}
            rep["problems"] += d["problems"]
        except FileNotFoundError as e:
            rep["problems"].append(str(e))
    out = _abs(s["save_model_dir"])
    if for_action == "train" and glob.glob(os.path.join(out, "*.pdparams")) and not s.get("_resume"):
        rep["problems"].append(f"{out} 에 이미 체크포인트가 있음 → save_model_dir 를 바꾸거나 --resume")
    return rep


def _list(v):
    return "[" + ",".join(json.dumps(x, ensure_ascii=False) for x in v) + "]"


def overrides(s: dict, action: str, checkpoint: str | None = None) -> list[str]:
    import yaml
    data = _abs(s["data_dir"])
    ldir = _abs(s.get("label_dir") or s["data_dir"])
    cfg = yaml.safe_load(open(os.path.join(_abs(s["paddleocr_repo"]), s["base_config"]), encoding="utf-8"))
    o = [f"Eval.dataset.data_dir={data}", f"Eval.dataset.label_file_list={_list(os.path.join(ldir, f) for f in s['val_list'])}"]
    if action == "train":
        o = [f"Global.pretrained_model={_abs(s['pretrained_model'])}",
             f"Global.save_model_dir={_abs(s['save_model_dir'])}",
             f"Global.epoch_num={int(s['epochs'])}",
             f"Optimizer.lr.learning_rate={float(s['learning_rate'])}",
             f"Train.dataset.data_dir={data}",
             f"Train.dataset.label_file_list={_list(os.path.join(ldir, f) for f in s['train_list'])}",
             f"Train.loader.batch_size_per_card={int(s['batch_size'])}"] + o
        if "ratio_list" in (cfg.get("Train", {}).get("dataset") or {}):
            o.append(f"Train.dataset.ratio_list={_list([1.0] * len(s['train_list']))}")
        if (cfg.get("Train", {}).get("sampler") or {}).get("first_bs") is not None:
            o.append(f"Train.sampler.first_bs={int(s['batch_size'])}")   # 다중 크기 샘플러가 있는 rec 설정
        for k, v in (s.get("extra_overrides") or {}).items():
            o.append(f"{k}={v}")
        if s.get("_resume"):
            o.append(f"Global.checkpoints={os.path.join(_abs(s['save_model_dir']), 'latest')}")
    elif action in ("eval", "export"):
        o = [f"Global.pretrained_model={checkpoint}"] + (o if action == "eval" else [])
        if action == "export":
            o.append(f"Global.save_inference_dir={_abs(s['save_model_dir'])}/inference")
    return o


def pick_checkpoint(s: dict, which: str) -> str:
    out = _abs(s["save_model_dir"])
    if which in ("best", "latest"):
        p = os.path.join(out, "best_accuracy.pdparams" if which == "best" else "latest.pdparams")
    else:
        p = _abs(which)
    if not os.path.exists(p):
        raise SetupError(f"체크포인트가 없습니다: {p}  (list 로 확인)")
    return p


def list_checkpoints(s: dict) -> list[dict]:
    out = _abs(s["save_model_dir"])
    rows = [{"file": os.path.basename(p), "mtime": time.strftime("%m-%d %H:%M", time.localtime(os.path.getmtime(p))),
             "mb": round(os.path.getsize(p) / 2**20, 1)} for p in sorted(glob.glob(os.path.join(out, "*.pdparams")))]
    log = os.path.join(out, "train.log")
    if os.path.exists(log):
        best = [l.strip() for l in open(log, encoding="utf-8", errors="ignore") if re.search(r"best metric", l)]
        if best:
            rows.append({"file": "train.log 마지막 best metric", "line": best[-1][-160:]})
    return rows


def command(s: dict, action: str, checkpoint: str | None = None) -> tuple[list[str], dict]:
    tool = {"train": "tools/train.py", "eval": "tools/eval.py", "export": "tools/export_model.py"}[action]
    cmd = [sys.executable, tool, "-c", s["base_config"], "-o", *overrides(s, action, checkpoint)]
    env = dict(os.environ, CUDA_VISIBLE_DEVICES=str(s["gpu"]))
    return cmd, env


def run(settings_path: str, action: str, checkpoint: str = "best", execute: bool = False, resume: bool = False) -> int:
    s = load_settings(settings_path)
    s["_resume"] = resume
    if action == "list":
        for r in list_checkpoints(s):
            print(r)
        return 0
    rep = check(s, "train" if action == "train" else "eval")
    for n in rep["notes"]:
        print("[참고]", n)
    if action == "check":
        print(json.dumps({k: v for k, v in rep.items() if k != "notes"}, ensure_ascii=False, indent=1))
        return 1 if rep["problems"] else 0
    if rep["problems"]:
        for p in rep["problems"]:
            print("[문제]", p)
        print("문제를 해결한 뒤 다시 실행하세요. 아무것도 실행하지 않았습니다.")
        return 1
    ck = None if action == "train" else pick_checkpoint(s, checkpoint)
    cmd, env = command(s, action, ck)
    repo = _abs(s["paddleocr_repo"])
    printable = f"cd {shlex.quote(repo)} && CUDA_VISIBLE_DEVICES={s['gpu']} " + " ".join(shlex.quote(c) for c in cmd)
    print(printable)
    if not execute:
        print("(출력만 함. 실행하려면 --run)")
        return 0
    os.makedirs(_abs(s["save_model_dir"]), exist_ok=True)
    return subprocess.run(cmd, cwd=repo, env=env).returncode
