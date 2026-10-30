# 사용한 오픈소스 목록 (Third-party notices)

각 라이브러리의 저작권·라이선스 고지는 원본 패키지에 포함된 그대로 유지합니다. 라이브러리 코드는 수정하지 않았습니다.
값은 GPU 서버에 설치된 패키지 정보(importlib.metadata)에서 자동으로 추출했습니다.

## OCR·API (venv)

| 패키지 | 버전 | 라이선스 |
|---|---|---|
| paddleocr | 3.7.0 | Apache License 2.0 |
| paddlepaddle-gpu | 3.3.1 | Apache Software License |
| paddlex | 3.7.2 | Apache-2.0 |
| opencv-python-headless | 5.0.0.93 | Apache 2.0 |
| numpy | 2.3.5 | Copyright (c) 2005-2025, NumPy Developer |
| fastapi | 0.162.0 | MIT |
| starlette | 1.10.0 | BSD-3-Clause |
| uvicorn | 0.54.0 | BSD-3-Clause |
| python-multipart | 0.0.32 | Apache-2.0 |
| mlflow | 3.17.2 | Apache Software License |

## YOLO 검출 (venv_yolo)

| 패키지 | 버전 | 라이선스 |
|---|---|---|
| ultralytics | 8.4.192 | AGPL-3.0 |
| torch | 2.11.0+cu128 | BSD-3-Clause |
| torchvision | 0.26.0+cu128 | BSD |

## 학습한 모델

- `models/receipt_det_yolo11n_seg.pt`: Ultralytics YOLO11n-seg 파생 가중치, AGPL-3.0 (`models/README.md`, `models/LICENSE`)
