import os

os.environ["CUDA_VISIBLE_DEVICES"] = ""   # 테스트는 GPU 를 쓰지 않는다 (다른 팀 GPU 보호)
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.getcwd())
sys.path.insert(0, os.path.dirname(HERE))
os.environ.setdefault("LAB_PROJECT", os.getcwd())
