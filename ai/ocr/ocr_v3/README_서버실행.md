# ocr_v3 서버 실행 안내 (GPU 서버, Ubuntu 터미널)

- 위치: `~/receipt_ocr/ocr_v3/` (Jupyter에 `ocr_v3.zip`을 올려 풀기)
- 모든 명령은 `~/receipt_ocr`에서 실행한다. GPU는 팀 배정 2번 (`CUDA_VISIBLE_DEVICES=2`)
- 운영 API 프로세스는 건드리지 않는다. 패키지 설치·모델 다운로드도 하지 않는다
- 실제 영수증 사진, 정답표, OCR 원문(진단 JSON·후보 파일)은 서버 안에만 둔다. GitHub에 올리지 않는다

## 0. 풀기

```
cd ~/receipt_ocr
```
```
venv/bin/python -m zipfile -e ocr_v3.zip .
```

## 1. 환경 점검 (읽기 전용)

```
CUDA_VISIBLE_DEVICES=2 venv/bin/python ocr_v3/lab.py env --out ocr_v3_env.json
```
- 버전(Python·paddle·paddleocr·paddlex·OpenCV), GPU 이름·메모리·드라이버, Paddle CUDA 빌드·GPU 인식, OpenCV의 EXIF 처리, 모델 캐시, 데이터·체크포인트, 실행 중인 API를 출력한다

선택 (작은 GPU 연산 + 운영 설정으로 OCR 초기화·빈 이미지 1장. 모델이 캐시에 없으면 초기화하지 않음):
```
CUDA_VISIBLE_DEVICES=2 venv/bin/python ocr_v3/lab.py env --paddle-run-check --init-ocr --out ocr_v3_env_gpu.json
```

## 2. 테스트 (GPU 안 씀: 실행기가 GPU 를 보이지 않게 하고 돌린다)

```
venv/bin/python ocr_v3/tests/run_tests.py
```

## 3. 사진 한 장 추론

```
CUDA_VISIBLE_DEVICES=2 venv/bin/python ocr_v3/lab.py infer real_new/dev2/20261102_093452.jpg
```
후보·선택 이유까지 저장 (OCR 원문 포함, 공유 금지):
```
CUDA_VISIBLE_DEVICES=2 venv/bin/python ocr_v3/lab.py infer real_new/dev2/20261102_093452.jpg --diag-out private_diag/20261102_093452.json
```
전처리 하나만 바꿔 보기 + 전처리 이미지 저장 (원본은 그대로):
```
CUDA_VISIBLE_DEVICES=2 venv/bin/python ocr_v3/lab.py infer real_new/dev2/20261102_093452.jpg --mode clahe --save-preprocessed private_pre
```

## 3-1. 사진 1장 원인 진단 (방향·OCR 원문·필드 추적·GPU 증거)

```
CUDA_VISIBLE_DEVICES=2 venv/bin/python ocr_v3/lab.py diagnose real_new/dev2/20261102_093452.jpg --also-cpu
```
- 결과 폴더: `private_diag/<사진이름>_<시각>/` (원본 사진·모델은 그대로)
- `report.md` 는 카드·승인·전화·사업자번호와 사람 이름을 가린 요약. 나머지 json 에는 OCR 원문이 들어 있으니 서버 밖으로 옮기지 않는다
- 정답을 알면 `--expect-date 2026-10-01 --expect-time 18:26` 처럼 넣어 OCR 에 그 숫자가 있었는지도 확인한다

## 4. dev2 평가 (정답표를 채운 뒤)

- 정답표 `real_new/dev2_labels.csv`: `address_road` 칸에 **API가 돌려줘야 할 주소(도로명까지)**를 적는다. 주소가 없으면 `-`
- 선택: `address_complete` 열을 추가해 true/false 를 적으면 addressComplete 정확도도 계산한다

```
CUDA_VISIBLE_DEVICES=2 venv/bin/python ocr_v3/lab.py eval --images real_new/dev2 --labels real_new/dev2_labels.csv --data-kind real --out results_v3/dev2_baseline
```
결과: `results_v3/dev2_baseline/metrics.json` (필드별 정확·누락·오인식, 3·4필드 정답률, p50/p95, 초기화 시간, 메모리, 버전·GPU), `rows.csv` (정오·시간)

틀린 필드의 원인 나누기 (탐지 누락 → det 학습 검토 / 글자 오인식 → rec 학습 검토 / 선택·파싱 → 후처리):
```
CUDA_VISIBLE_DEVICES=2 venv/bin/python ocr_v3/lab.py eval --images real_new/dev2 --labels real_new/dev2_labels.csv --data-kind real --out results_v3/dev2_raw --save-raw
```
```
venv/bin/python classify_errors.py --results results_v3/dev2_raw --out results_v3/dev2_error_causes
```
- 상호명·날짜·시간은 `results_v3/dev2_error_causes/`, 주소는 `results_v3/dev2_raw/address_error_causes.csv` (판정만, 원문 없음)
- `raw/` 와 `per_receipt.csv` 에는 OCR 원문·정답이 들어 있으니 서버 밖으로 옮기지 않는다

## 4-1. 후처리 규칙만 바꿔 다시 채점 (OCR·GPU 안 씀)

`--save-raw` 로 평가한 폴더의 OCR 결과로 필드 추출만 다시 한다. 먼저 바꾸기 전 채점이 그 폴더의 `rows.csv`·`metrics.json` 과 같은지 확인하고, 다르면 멈춘다.
```
venv/bin/python ocr_v3/rescore_raw.py --labels real_new/dev2_labels.csv --runs results_v3/dev2_1280_raw results_v3/dev2_1280_rec_ft2_raw --set fields.date_lookalike_fix=fallback --out results_v3/rescore_datefix_20261104
```
- `fields.date_lookalike_fix=fallback` (실험, 기본 `legacy` = 운영과 같음): 날짜를 못 찾았을 때만 날짜 모양 안의 O·o·C·c·D→0, I·l·|→1 로 바꿔 한 번 더 찾는다. 이미 찾은 날짜는 바꾸지 않는다
- `fields.name_rule=v3` (실험, 11/09): 상호명 v2 + 카드 전표 '가맹점/사업자번호' 다음 줄의 상호, 사업자번호 뒤 상호, 합쳐진 두 칸 라벨, 안내 문구·번지 제외, '…점 + 한글 브랜드 한 단어' 유지 (`ocrlab/fields.py` `name_v3`)
- `fields.addr_lookalike_fix=on` (실험, 11/09): 주소 추출 때만 '…로85일 25' → '…로85길 25' ('길'을 '일'로 읽은 경우)
- `fields.line_group=v2` (실험, 11/09, 채택 보류): 실제 글자 높이·줄별 기울기로 줄 묶기 (`ocrlab/lines.py`). 다시 채점·평가 모두 이 설정을 따른다
- 여러 값을 함께 바꾸려면 `--set` 을 여러 번 쓴다: `--set fields.name_rule=v3 --set fields.addr_lookalike_fix=on`
- 출력·`rescore.json` 에는 정답 수와 판정 변화만 있다 (OCR 원문·추출값 없음). holdout 경로는 거부한다

## 5. 전처리·방향 설정 비교 (dev2, 모드마다 한 요소만 바꿈)

```
CUDA_VISIBLE_DEVICES=2 venv/bin/python ocr_v3/lab.py compare --images real_new/dev2 --labels real_new/dev2_labels.csv --data-kind real --out results_v3/dev2_compare --modes baseline clahe denoise binarize exif_explicit docori textline unwarp
```
- `docori`·`textline`·`unwarp`는 해당 모델이 캐시에 없으면 그 모드만 멈춘다 (다운로드 방지)
- 다른 값 하나만 바꾸기: `--set ocr.text_det_thresh=0.3` 또는 `--set resize.max_side=1600`

## 6. holdout2 (규칙·설정을 다 정한 뒤 마지막에 한 번만)

```
CUDA_VISIBLE_DEVICES=2 venv/bin/python ocr_v3/lab.py eval --images real_new/holdout2 --labels real_new/holdout2_labels.csv --data-kind real --out results_v3/holdout2_final --final
```

## 7. 줄 단위 정답 만들기 (파인튜닝·CER 준비)

① OCR로 **후보** 만들기 (정답 아님. 회전·1280 축소를 반영한 사본에 좌표를 맞춤. 학습용 사진은 dev2·holdout2 와 다른 영수증):
```
CUDA_VISIBLE_DEVICES=2 venv/bin/python ocr_v3/lab.py candidates --images real_new/train1 --out labeling/train1_candidates --set resize.max_side=1280
```
② 사람이 검수: `labeling/dev2_candidates/AUTO_CANDIDATES_not_verified.jsonl`을 복사해 `dev2_reviewed.jsonl`로 만들고, 줄마다 글자·좌표를 고친 뒤 다 본 사진만 `"reviewed": true`로 바꾼다
(또는 PC의 PPOCRLabel에서 `Label_CANDIDATES_not_verified.txt`를 고친 뒤 `dataset import-ppocrlabel`)

③ 검수한 것만 정답으로:
```
venv/bin/python ocr_v3/lab.py dataset promote --manifest labeling/dev2_reviewed.jsonl --out labeling/dev2_verified.jsonl --reviewer 김재원
```
④ 검사·분할·내보내기:
```
venv/bin/python ocr_v3/lab.py dataset check --manifest labeling/dev2_verified.jsonl --root labeling/dev2_candidates --dict PaddleOCR/ppocr/utils/dict/ppocrv5_korean_dict.txt --max-len 25
```
```
venv/bin/python ocr_v3/lab.py dataset split --manifest labeling/dev2_verified.jsonl --root labeling/dev2_candidates --out labeling/dev2_split.jsonl
```
```
venv/bin/python ocr_v3/lab.py dataset export-rec --manifest labeling/dev2_split.jsonl --root labeling/dev2_candidates --out rec_real --dict PaddleOCR/ppocr/utils/dict/ppocrv5_korean_dict.txt --max-len 25
```
```
venv/bin/python ocr_v3/lab.py dataset export-det --manifest labeling/dev2_split.jsonl --root labeling/dev2_candidates --out det_real
```

## 8. 사전·출력층·가중치 호환성

```
venv/bin/python ocr_v3/lab.py dictcheck --dict PaddleOCR/ppocr/utils/dict/ppocrv5_korean_dict.txt --inference-yml ~/.paddlex/official_models/korean_PP-OCRv5_mobile_rec/inference.yml --pretrained pretrained/korean_PP-OCRv5_mobile_rec_pretrained.pdparams
```

## 9. 파인튜닝 (점검 → 명령 확인 → 실행)

```
venv/bin/python ocr_v3/lab.py finetune --settings ocr_v3/configs/finetune_rec.yaml --action check
```
```
bash ocr_v3/scripts/train.sh ocr_v3/configs/finetune_rec.yaml
```
↑ 명령만 출력. 실제 학습(오래 걸림):
```
bash ocr_v3/scripts/train.sh ocr_v3/configs/finetune_rec.yaml --run
```
검증·체크포인트 확인·내보내기:
```
bash ocr_v3/scripts/eval.sh ocr_v3/configs/finetune_rec.yaml best --run
```
```
bash ocr_v3/scripts/export.sh ocr_v3/configs/finetune_rec.yaml best --run
```
검출(det)은 `ocr_v3/configs/finetune_det.yaml`로 같은 명령 (사전학습 가중치 파일이 서버에 있는지 먼저 check).

## 10. 학습한 모델 평가

CER (검수 정답이 있을 때만 계산. 없으면 이유를 출력하고 건너뜀):
```
CUDA_VISIBLE_DEVICES=2 venv/bin/python ocr_v3/lab.py cer --labels rec_real/test.txt --model-dir output/rec_real_v1/inference
```
전체 파이프라인 (dev2, 인식 모델만 교체):
```
CUDA_VISIBLE_DEVICES=2 venv/bin/python ocr_v3/lab.py eval --images real_new/dev2 --labels real_new/dev2_labels.csv --data-kind real --out results_v3/dev2_rec_real_v1 --set ocr.text_recognition_model_dir=output/rec_real_v1/inference --set ocr.text_detection_model_name=PP-OCRv5_server_det --set ocr.text_recognition_model_name=korean_PP-OCRv5_mobile_rec
```

## 11. 운영 API에 응답 규격 v3 적용 (백엔드 DTO 준비 후)

```
venv/bin/python ocr_v3/patch_v3.py --dry-run
```
```
venv/bin/python ocr_v3/patch_v3.py
```
```
venv/bin/python ocr_v3/tests/run_tests.py
```
- 백업: `*_before_v3.py`. API 재시작은 백엔드·앱과 배포 순서를 맞춘 뒤 따로 한다
