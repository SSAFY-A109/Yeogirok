#!/usr/bin/env bash
# 서버에서 실행한다. AI(영수증 OCR) 컨테이너가 실제로 글자를 읽는지 확인한다.
#
#   bash scripts/check-ai.sh        # 마지막 줄이 "[ai] 성공" 이면 정상
#
# /health 는 모델을 불러오기만 하면 200 이라, OCR 이 실제로 도는지는 그림 한 장을 읽혀 봐야 안다.
# (11/9: CPU 가속 오류로 /health 는 200 인데 모든 OCR 요청이 500 이었다.)
# 컨테이너 안에서 시험용 영수증 그림을 만들어 http://127.0.0.1:8000/ocr/receipt 로 보낸다. 바깥 포트는 열지 않는다.

set -euo pipefail
cd "$(dirname "$0")/.."

if [[ -z "$(docker compose ps -q ai 2>/dev/null)" ]]; then
  echo "[ai] 실패: ai 컨테이너가 없습니다. docker compose up -d --build ai 로 켭니다 (가이드 9단계 AI 서버)"
  exit 1
fi

docker compose exec -T ai python - <<'PY'
import io, json, time, urllib.error, urllib.request, uuid
from PIL import Image, ImageDraw, ImageFont

# 컨테이너에 한글 글꼴이 없어 영문·숫자만 쓴다. 날짜가 읽히면 검출·인식·후처리가 모두 도는 것이다.
img = Image.new("RGB", (640, 360), "white")
draw = ImageDraw.Draw(img)
font = ImageFont.load_default(size=36)
for i, text in enumerate(["YEOGIROK TEST", "2026-11-09 14:23", "TOTAL 20,500"]):
    draw.text((30, 40 + i * 100), text, fill="black", font=font)
jpg = io.BytesIO()
img.save(jpg, "JPEG", quality=92)

boundary = uuid.uuid4().hex
body = (f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="check.jpg"\r\n'
        f"Content-Type: image/jpeg\r\n\r\n").encode() + jpg.getvalue() + f"\r\n--{boundary}--\r\n".encode()
req = urllib.request.Request("http://127.0.0.1:8000/ocr/receipt", data=body,
                             headers={"Content-Type": f"multipart/form-data; boundary={boundary}"})
start = time.time()
try:
    with urllib.request.urlopen(req, timeout=120) as res:
        out = json.load(res)
except urllib.error.HTTPError as e:
    print(f"[ai] 실패: HTTP {e.code} {e.read().decode(errors='replace')}")
    print("[ai] 오류 내용: docker compose logs --tail 80 ai")
    raise SystemExit(1)
except OSError as e:
    print(f"[ai] 실패: AI 서버에 연결하지 못했습니다 ({e}). 아직 켜지는 중이면 docker compose ps ai 가 (healthy) 가 된 뒤 다시 실행합니다")
    raise SystemExit(1)

print(f"[ai] 응답 {time.time() - start:.1f}초: {json.dumps(out, ensure_ascii=False)}")
if out.get("date") != "2026-11-09":
    print("[ai] 실패: 시험 그림의 날짜(2026-11-09)를 읽지 못했습니다. 오류 내용: docker compose logs --tail 80 ai")
    raise SystemExit(1)
print("[ai] 성공: OCR 이 글자를 읽습니다")
PY
