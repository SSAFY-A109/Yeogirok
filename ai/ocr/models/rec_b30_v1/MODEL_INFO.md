# rec_b30_v1 — 글자 인식 최종 모델 B30 (S15P31A109-49)

- 역할: 글자 인식만 (잘라낸 글자 줄 이미지 → 문자열). 글자 검출은 공식 PP-OCRv5_server_det 그대로
- 시작 가중치: korean_PP-OCRv5_mobile_rec 공식 사전학습 가중치 (PaddleOCR)
- 학습: 실제 영수증 글자 줄 1,267줄 (train1 202 + train2 1,065, 사람 검수 정답), 30 epoch, 학습률 0.00005
- 검증: 학습에 쓰지 않은 검증 83줄에서 글자 오류율(CER) 2.55% — 글자 단위 수치이며 영수증 전체 정확도가 아님
- 영수증 단위 평가와 채택 근거: AI 실험 기록(실험결과.md) E8·E11·E17
- 되돌리기: 환경변수 OCR_REC_MODEL_DIR=official
- 내보내기: paddle 3.2.2 (운영 Dockerfile 의 CPU paddle 과 같은 버전), PaddleOCR 저장소 dab3fe3, 체크포인트 best_accuracy (11/04 학습)
  - 11/13 다시 내보냄: 11/04 에 paddle 3.3.1 로 내보낸 파일은 paddle 3.2.2 에서 불러오기 실패 (pd_op_to_kernel_pass.cc:2597)
  - 다시 학습하지 않음. 같은 체크포인트이며, 서버 GPU 에서 dev2 29장 결과가 11/04 파일과 같음을 확인
  - 앞으로 이 모델을 다시 내보낼 때도 운영 paddle 버전과 같은 버전으로 내보낼 것

| 파일 | 크기 (bytes) | md5 |
|---|---|---|
| inference.json | 217,724 | 228a3452e95c70e0b19646a35896f271 |
| inference.pdiparams | 13,342,671 | d69ba074dc43ddb7770ed46bbec87c36 |
| inference.yml | 96,039 | db0295874ec67df2a56196555e963971 |
