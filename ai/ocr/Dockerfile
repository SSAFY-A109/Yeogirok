# 영수증 OCR 서버 이미지 (CPU). ai/ocr 폴더를 빌드 컨텍스트로 쓴다.
#
#   docker build -t yeogirok-ai ai/ocr                                # 저장소 루트에서
#   docker run --rm -p 127.0.0.1:8000:8000 yeogirok-ai                # http://localhost:8000/health
#
# 서버에서는 infra/docker-compose.yml 의 ai 서비스가 이 파일로 이미지를 만든다.
# 서버에 GPU 가 없어서 requirements.txt 의 paddlepaddle-gpu 를 CPU 용 paddlepaddle 로 바꿔 설치한다.
# 학습·실험에만 쓰는 패키지(mlflow, matplotlib)는 넣지 않는다. 나머지 버전은 requirements.txt 를 그대로 따른다.

FROM python:3.12-slim

# CPU 용 paddlepaddle 버전. GPU 용(requirements.txt, 3.3.1)과 다르게 3.2.2 로 고정한다.
# 3.3.0·3.3.1 CPU 판은 CPU 가속(oneDNN)을 켜면 검출 모델에서 바로 실패한다
#   NotImplementedError: ConvertPirAttribute2RuntimeAttribute not support [pir::ArrayAttribute<pir::DoubleAttribute>]
# 가속을 끄면 돌지만 영수증 1장이 약 6초 걸린다. 3.2.2 는 가속을 켠 채 약 1초이고 인식 결과가 같다(11/9 노트북 시험).
# 새 버전으로 올릴 때는 영수증 1장을 실제로 OCR 해서 확인한다(infra 가이드 9단계).
ARG PADDLE_CPU_VERSION=3.2.2

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1

# libgl1·libglib2.0-0: opencv-contrib-python(paddlex 가 설치)이 불러오는 화면 라이브러리. 없으면 import cv2 가 실패한다.
# libgomp1: paddle 의 CPU 병렬 연산 라이브러리.
RUN apt-get update \
 && apt-get install -y --no-install-recommends libgl1 libglib2.0-0 libgomp1 \
 && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# 의존성만 먼저 설치한다. 코드만 바뀐 배포에서는 이 층을 캐시로 재사용한다.
COPY requirements.txt .
RUN sed -e 's/\r$//' \
        -e "s/^paddlepaddle-gpu==.*/paddlepaddle==${PADDLE_CPU_VERSION}/" \
        -e '/^mlflow==/d' \
        -e '/^matplotlib==/d' \
        requirements.txt > /tmp/requirements-server.txt \
 && pip install -r /tmp/requirements-server.txt

# root 가 아닌 사용자로 실행한다. OCR 모델은 처음 켤 때 ~/.paddlex 에 내려받는다.
# compose 가 이 폴더에 볼륨을 붙여, 다시 배포해도 모델을 새로 받지 않는다.
RUN groupadd --system app \
 && useradd --system --gid app --create-home --home-dir /home/app app \
 && mkdir -p /home/app/.paddlex \
 && chown app:app /home/app/.paddlex

# 코드는 최상위 .py 만 들어온다(.dockerignore). 실제 영수증 사진·학습 데이터·모델 가중치는 이미지에 넣지 않는다.
COPY *.py ./
USER app

# 서버에 GPU 가 없다. app.py 의 기본값(gpu:0)을 CPU 로 바꾼다.
ENV OCR_DEVICE=cpu
EXPOSE 8000
# OCR 모델은 메모리를 많이 써서 프로세스(worker)를 하나만 띄운다.
CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8000"]
