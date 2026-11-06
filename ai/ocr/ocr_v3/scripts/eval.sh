#!/usr/bin/env bash
# 검증. 사용: bash ocr_v3/scripts/eval.sh ocr_v3/configs/finetune_rec.yaml best [--run]
set -euo pipefail
cd ~/receipt_ocr
venv/bin/python ocr_v3/lab.py finetune --settings "$1" --action eval --checkpoint "${2:-best}" "${@:3}"
