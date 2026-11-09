#!/usr/bin/env bash
# 백업 파일로 서버 DB를 되돌린다 (가이드 8단계). 지금 DB 내용은 모두 백업 시점으로 바뀐다.
#
#   restore-db.sh ~/yeogirok/backups/yeogirok-2026-11-10-040001.sql.gz
#
# 순서: 지금 상태를 한 번 더 백업 → 백엔드 멈춤 → DB를 지우고 새로 만듦 → 백업 파일 붓기 → 백엔드 다시 켬.
# 백업 파일을 지금 DB에 그냥 부으면 테이블이 이미 있어 "already exists" 오류만 나고 데이터는 돌아오지 않는다(11/9 시험).
# 그래서 DB를 비우고 붓는다. 붓다가 오류가 나면 바로 멈춘다(ON_ERROR_STOP).

set -euo pipefail

FILE="${1:-}"
if [[ -z "$FILE" ]]; then
  echo "[restore] 사용법: restore-db.sh <백업 파일>. 백업 목록: ls -lh ~/yeogirok/backups"
  exit 1
fi
[[ -f "$FILE" ]] || { echo "[restore] 파일이 없습니다: $FILE"; exit 1; }
gunzip -t "$FILE" 2>/dev/null || { echo "[restore] 압축 파일이 깨져 있습니다: $FILE"; exit 1; }
FILE="$(cd "$(dirname "$FILE")" && pwd)/$(basename "$FILE")"

cd "$(dirname "$0")/.."
ROOT="${YEOGIROK_ROOT:-$HOME/yeogirok}"
HEALTH_URL="${HEALTH_URL:-http://127.0.0.1:8080/v3/api-docs}"

echo "[restore] $FILE 로 DB 를 되돌립니다. 지금 DB 내용은 모두 백업 시점으로 바뀌고, 그동안 백엔드가 멈춥니다."
read -r -p "계속하려면 restore 를 입력합니다: " answer
[[ "$answer" == restore ]] || { echo "[restore] 그만둡니다. 바뀐 것은 없습니다."; exit 1; }

echo "[restore] 1/5 지금 상태를 먼저 백업합니다 (복구를 되돌리고 싶을 때 이 파일로 다시 restore-db.sh 를 실행)"
bash scripts/backup-db.sh
PRE="$(ls -t "$ROOT"/backups/yeogirok-*.sql.gz | head -1)"

echo "[restore] 2/5 백엔드를 멈춥니다"
docker compose stop backend

echo "[restore] 3/5 DB 를 지우고 새로 만듭니다"
docker compose exec -T db sh -c 'dropdb -U "$POSTGRES_USER" --force "$POSTGRES_DB" && createdb -U "$POSTGRES_USER" "$POSTGRES_DB"'

echo "[restore] 4/5 백업 파일을 붓습니다"
if ! gunzip -c "$FILE" | docker compose exec -T db sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 -q' >/dev/null; then
  echo "[restore] 실패: 붓다가 오류가 나서 멈췄습니다. 백엔드는 멈춘 채로 둡니다."
  echo "[restore] 복구 전 상태로 돌아가려면 1단계에서 만든 백업으로 다시 실행합니다:"
  echo "[restore]   bash scripts/restore-db.sh $PRE"
  exit 1
fi

echo "[restore] 5/5 백엔드를 다시 켭니다"
docker compose start backend
for _ in $(seq 1 40); do
  if curl -fsS -o /dev/null "$HEALTH_URL"; then
    echo "[restore] 성공: DB 를 $(basename "$FILE") 시점으로 되돌렸고 백엔드가 응답합니다"
    exit 0
  fi
  sleep 3
done
echo "[restore] DB 는 되돌렸지만 백엔드가 120초 안에 응답하지 않습니다. docker compose logs --tail 100 backend 로 확인합니다"
exit 1
