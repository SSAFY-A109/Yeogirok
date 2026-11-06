"""pytest 가 없어도 돌아가는 테스트 실행기. 사용: cd ~/receipt_ocr && venv/bin/python ocr_v3/tests/run_tests.py"""
import importlib
import os

os.environ["CUDA_VISIBLE_DEVICES"] = ""   # 테스트는 GPU 를 쓰지 않는다 (다른 팀 GPU 보호)
import sys
import traceback

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.getcwd())                     # receipt_parser, eval_baseline (운영 코드)
sys.path.insert(0, os.path.dirname(HERE))           # ocr_v3: address_parser, ocr_response(v3), ocrlab
sys.path.insert(0, HERE)
os.environ.setdefault("LAB_PROJECT", os.getcwd())

ok = fail = 0
for f in sorted(os.listdir(HERE)):
    if not (f.startswith("test_") and f.endswith(".py")):
        continue
    mod = importlib.import_module(f[:-3])
    for name in [n for n in dir(mod) if n.startswith("test_")]:
        try:
            getattr(mod, name)()
            ok += 1
        except Exception:
            fail += 1
            print(f"FAIL {f}::{name}")
            traceback.print_exc(limit=3)
print(f"\n통과 {ok}개, 실패 {fail}개")
sys.exit(1 if fail else 0)
