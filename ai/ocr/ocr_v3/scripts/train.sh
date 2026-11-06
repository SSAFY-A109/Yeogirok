#!/usr/bin/env bash
# 단일 GPU 학습. 사용: bash ocr_v3/scripts/train.sh ocr_v3/configs/finetune_rec.yaml [--run]
# --run 이 없으면 점검 후 명령만 출력한다.
set -euo pipefail
cd ~/receipt_ocr
venv/bin/python ocr_v3/lab.py finetune --settings "$1" --action train "${@:2}"
