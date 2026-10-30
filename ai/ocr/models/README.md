# 영수증 영역 검출 모델 (receipt_det_yolo11n_seg.pt)

## 라이선스 (AGPL-3.0)
- 이 가중치는 Ultralytics **YOLO11n-seg** 사전학습 가중치(AGPL-3.0)를 파인튜닝한 파생물이며, **AGPL-3.0**을 따릅니다.
- 원본 라이선스 전문: 같은 폴더의 `LICENSE` (ultralytics 8.4.192 패키지에 포함된 파일을 수정 없이 복사)
- 원본: Ultralytics YOLO — https://github.com/ultralytics/ultralytics (Copyright Ultralytics)

## 수정 사항 고지
| 항목 | 내용 |
|---|---|
| 수정일 | 2026-10-29 |
| 수정 내용 | 영수증 1개 클래스(receipt) 분할 모델로 파인튜닝 (원본 코드·라이브러리는 수정하지 않음) |
| 학습 데이터 | 코드로 생성한 합성 영수증 + 코드로 생성한 배경 (train 1500 / val 200 / test 300), 실제 영수증 사진 미사용 — 생성 코드: `../make_det_data.py` |
| 학습 설정 | epochs 50, imgsz 640, batch 32, seed 0 (`yolo segment train model=yolo11n-seg.pt data=det_data/data.yaml ...`) |
| 실행 환경 | `../requirements-yolo.txt` (ultralytics 8.4.192, torch 2.11.0+cu128) — OCR 환경과 분리된 가상환경 |

## 성능 (합성 배경 test 300장)
- 검증 mask mAP50-95 0.995 (best epoch 18), 꼭짓점 IoU 평균 0.980, 찾은 비율 100%
- 같은 조건 비교: yolo11s·yolov8n과 정확도 동일, yolo11n이 가장 빠름 → 이 모델 선택
- 기록: MLflow `receipt-ocr` 실험의 `yolo_v1_det` run

## 사용 현황
- 영수증 사진 → 영수증 네 꼭짓점 검출 → 원근 보정 전처리 실험에 사용
- **현재 OCR API(app.py)에는 포함하지 않음** (실험·비교용, 서비스 적용은 추가 평가 후 결정)

## 무결성
- SHA-256: `530c2fef00013abc74035fa0a733d1481201059cb43e4ff78e3d749882a8e2a3`
- 크기: 약 5.8MB
