#!/usr/bin/env bash
# 체크포인트 선택 후 추론 모델 내보내기. 사용: bash ocr_v3/scripts/export.sh ocr_v3/configs/finetune_rec.yaml best [--run]
set -euo pipefail
cd ~/receipt_ocr
venv/bin/python ocr_v3/lab.py finetune --settings "$1" --action list
venv/bin/python ocr_v3/lab.py finetune --settings "$1" --action export --checkpoint "${2:-best}" "${@:3}"
