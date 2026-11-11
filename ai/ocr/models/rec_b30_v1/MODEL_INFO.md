# rec_b30_v1 — 글자 인식 최종 모델 B30 (S15P31A109-49)

- 역할: 글자 인식만 (잘라낸 글자 줄 이미지 → 문자열). 글자 검출은 공식 PP-OCRv5_server_det 그대로
- 시작 가중치: korean_PP-OCRv5_mobile_rec 공식 사전학습 가중치 (PaddleOCR)
- 학습: 실제 영수증 글자 줄 1,267줄 (train1 202 + train2 1,065, 사람 검수 정답), 30 epoch, 학습률 0.00005
- 검증: 학습에 쓰지 않은 검증 83줄에서 글자 오류율(CER) 2.55% — 글자 단위 수치이며 영수증 전체 정확도가 아님
- 영수증 단위 평가와 채택 근거: AI 실험 기록(실험결과.md) E8·E11·E17
- 되돌리기: 환경변수 OCR_REC_MODEL_DIR=official

| 파일 | 크기 (bytes) | md5 |
|---|---|---|
| inference.json | 214,476 | 4db87573175dc80b9c1b09d4df5d4f7d |
| inference.pdiparams | 13,342,671 | 1f4ee6f11fc8c3061feebc58f829f91c |
| inference.yml | 96,039 | db0295874ec67df2a56196555e963971 |
