"""학습 준비: 설정 검사·허용 설정·명령 생성·체크포인트·사전 호환성 (가짜 저장소, GPU 불필요)."""
import json
import os
import tempfile

import yaml

from ocrlab import dictcheck, finetune

REC = "configs/rec/PP-OCRv5/multi_language/korean_PP-OCRv5_mobile_rec.yml"


def _repo(d):
    for f in ("tools/train.py", "tools/eval.py", "tools/export_model.py"):
        os.makedirs(os.path.dirname(os.path.join(d, f)), exist_ok=True)
        open(os.path.join(d, f), "w").write("")
    os.makedirs(os.path.join(d, os.path.dirname(REC)), exist_ok=True)
    os.makedirs(os.path.join(d, "ppocr/utils/dict"), exist_ok=True)
    open(os.path.join(d, "ppocr/utils/dict/ko.txt"), "w", encoding="utf-8").write("가\n나\n다\n")
    yaml.safe_dump({"Global": {"character_dict_path": "ppocr/utils/dict/ko.txt", "use_space_char": True},
                    "Train": {"dataset": {"ratio_list": [1.0]}, "sampler": {"first_bs": 128}}},
                   open(os.path.join(d, REC), "w"))


def _settings(d, **kw):
    s = {"task": "rec", "paddleocr_repo": os.path.join(d, "repo"), "base_config": REC,
         "pretrained_model": os.path.join(d, "pre.pdparams"), "data_dir": os.path.join(d, "data"),
         "train_list": ["train.txt"], "val_list": ["val.txt"], "save_model_dir": os.path.join(d, "out"),
         "epochs": 2, "learning_rate": 0.0002, "batch_size": 8, "gpu": 2}
    s.update(kw)
    p = os.path.join(d, "s.yaml")
    yaml.safe_dump(s, open(p, "w"))
    return p


def test_unsupported_config_refused():
    d = tempfile.mkdtemp()
    for bad in ({"base_config": "configs/rec/PP-OCRv6/x.yml"}, {"task": "cls"}, {"epochs": None}):
        try:
            finetune.load_settings(_settings(d, **bad))
            raise AssertionError(bad)
        except finetune.SetupError:
            pass


def test_check_reports_missing_and_candidates():
    d = tempfile.mkdtemp()
    _repo(os.path.join(d, "repo"))
    s = finetune.load_settings(_settings(d))
    probs = "\n".join(finetune.check(s)["problems"])
    assert "사전학습 가중치 없음" in probs and "라벨 목록 없음" in probs
    os.makedirs(os.path.join(d, "data"))
    open(os.path.join(d, "pre.pdparams"), "w").write("")
    for f in ("train.txt", "val.txt"):
        open(os.path.join(d, "data", f), "w", encoding="utf-8").write("crops/a.png\t가나\n")
    json.dump({"task": "rec", "sources": ["human_verified", "auto_candidate"]}, open(os.path.join(d, "data", "export_meta.json"), "w"))
    assert any("auto_candidate" in p for p in finetune.check(s)["problems"])
    json.dump({"task": "rec", "sources": ["human_verified"]}, open(os.path.join(d, "data", "export_meta.json"), "w"))
    rep = finetune.check(s)
    assert not [p for p in rep["problems"] if "가중치" not in p], rep["problems"]   # 가짜 가중치 파일 → paddle 로 못 읽는 것만 남음


def test_overrides_and_commands():
    d = tempfile.mkdtemp()
    _repo(os.path.join(d, "repo"))
    s = finetune.load_settings(_settings(d))
    o = finetune.overrides(s, "train")
    assert f"Global.pretrained_model={os.path.join(d, 'pre.pdparams')}" in o and "Train.sampler.first_bs=8" in o
    assert any(x.startswith("Train.dataset.label_file_list=[") for x in o) and "Train.dataset.ratio_list=[1.0]" in o
    cmd, env = finetune.command(s, "train")
    assert cmd[1:4] == ["tools/train.py", "-c", REC] and env["CUDA_VISIBLE_DEVICES"] == "2"
    try:
        finetune.pick_checkpoint(s, "best")
        raise AssertionError
    except finetune.SetupError:
        pass
    os.makedirs(os.path.join(d, "out"))
    open(os.path.join(d, "out", "best_accuracy.pdparams"), "w").write("")
    ck = finetune.pick_checkpoint(s, "best")
    e = finetune.overrides(s, "export", ck)
    assert e == [f"Global.pretrained_model={ck}", f"Global.save_inference_dir={os.path.join(d, 'out')}/inference"]
    assert finetune.list_checkpoints(s)[0]["file"] == "best_accuracy.pdparams"
    assert any("이미 체크포인트" in p for p in finetune.check(s)["problems"])


def test_dictcheck():
    d = tempfile.mkdtemp()
    dp = os.path.join(d, "dict.txt")
    open(dp, "w", encoding="utf-8").write("가\n나\n")
    inf = os.path.join(d, "inference.yml")
    yaml.safe_dump({"PostProcess": {"character_dict": ["가", "나"]}}, open(inf, "w"), allow_unicode=True)
    lab = os.path.join(d, "train.txt")
    open(lab, "w", encoding="utf-8").write("a.png\t가나\nb.png\t가다\n")
    r = dictcheck.run(dp, inf, None, True, [lab])
    assert r["dict_equal"] and r["expected_ctc_classes"] == 4 and r["labels"][lab]["oov_lines"] == 1 and not r["ok"]
    yaml.safe_dump({"PostProcess": {"character_dict": ["가", "다"]}}, open(inf, "w"), allow_unicode=True)
    assert dictcheck.run(dp, inf)["dict_equal"] is False


def test_broken_pretrained_reported_not_crash():
    import sys
    import types
    d = tempfile.mkdtemp()
    dp = os.path.join(d, "dict.txt")
    open(dp, "w", encoding="utf-8").write("가\n")
    pre = os.path.join(d, "empty.pdparams")
    open(pre, "w").write("")
    fake = types.ModuleType("paddle")

    def load(path):
        raise EOFError("Ran out of input")
    fake.load = load
    old = sys.modules.get("paddle")
    sys.modules["paddle"] = fake
    try:
        r = dictcheck.run(dp, None, pre)
    finally:
        if old is None:
            sys.modules.pop("paddle", None)
        else:
            sys.modules["paddle"] = old
    assert not r["ok"] and any("읽지 못함" in p and "EOFError" in p for p in r["problems"])
