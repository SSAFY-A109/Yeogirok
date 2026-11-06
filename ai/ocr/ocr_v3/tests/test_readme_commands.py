"""README_서버실행.md 와 scripts/*.sh 의 lab.py 명령이 실제 CLI 옵션과 맞는지 (파싱만, 실행 안 함)."""
import importlib.util
import os
import re
import shlex

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def _lab():
    spec = importlib.util.spec_from_file_location("lab_cli", os.path.join(ROOT, "lab.py"))
    m = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(m)
    seen = []
    for name in [n for n in dir(m) if n.startswith("cmd_")]:
        setattr(m, name, lambda a, _n=name: seen.append(_n))
    return m, seen


def _argv(line):
    toks = shlex.split(line)
    return toks[toks.index("ocr_v3/lab.py") + 1:]


def test_readme_commands_parse():
    lab, seen = _lab()
    text = open(os.path.join(ROOT, "README_서버실행.md"), encoding="utf-8").read()
    cmds = [l for l in text.splitlines() if "ocr_v3/lab.py" in l and not l.startswith(("-", ">", "#", "(", "↑"))]
    assert len(cmds) >= 18, len(cmds)
    for c in cmds:
        lab.main(_argv(c))
    assert len(seen) == len(cmds)


def test_script_commands_parse():
    lab, seen = _lab()
    for sh, extra in (("train.sh", ["--run"]), ("eval.sh", []), ("export.sh", [])):
        src = open(os.path.join(ROOT, "scripts", sh), encoding="utf-8").read()
        for line in [l for l in src.splitlines() if "ocr_v3/lab.py" in l]:
            line = (line.replace('"$1"', "ocr_v3/configs/finetune_rec.yaml").replace('"${2:-best}"', "best")
                    .replace('"${@:2}"', " ".join(extra)).replace('"${@:3}"', "--run"))
            lab.main(_argv(line))
    assert len(seen) == 4


def test_readme_paths_exist_in_bundle():
    text = open(os.path.join(ROOT, "README_서버실행.md"), encoding="utf-8").read()
    for p in set(re.findall(r"ocr_v3/((?:configs|scripts|tests)/[\w./]+)", text)) | {"patch_v3.py", "lab.py"}:
        assert os.path.exists(os.path.join(ROOT, p)), p
