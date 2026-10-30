#!/usr/bin/env bash
# 서버 DB를 압축 백업하고, 오래된 백업은 지운다. cron으로 매일 실행한다 (가이드 8단계).
#
#   backup-db.sh                    # 지금 백업
#   KEEP_DAYS=14 backup-db.sh       # 보관 기간 바꾸기 (기본 7일)
#
# 복구: gunzip -c <백업파일> | docker compose exec -T db sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"'

set -euo pipefail

ROOT="${YEOGIROK_ROOT:-$HOME/yeogirok}"
INFRA="$ROOT/infra-src/infra"
OUT="$ROOT/backups"
KEEP_DAYS="${KEEP_DAYS:-7}"

mkdir -p "$OUT"
cd "$INFRA"

FILE="$OUT/yeogirok-$(date +%F-%H%M).sql.gz"
# DB 계정 정보는 컨테이너 안의 환경변수를 그대로 쓴다 (작은따옴표라 컨테이너 안에서 풀린다).
docker compose exec -T db sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB"' | gzip > "$FILE"

find "$OUT" -name 'yeogirok-*.sql.gz' -mtime +"$KEEP_DAYS" -delete
echo "[backup] $(date '+%F %T') $FILE ($(du -h "$FILE" | cut -f1))"
