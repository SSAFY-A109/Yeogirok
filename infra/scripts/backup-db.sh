#!/usr/bin/env bash
# 서버 DB를 압축 백업하고, 오래된 백업은 지운다. cron으로 매일 실행한다 (가이드 8단계).
#
#   backup-db.sh                    # 지금 백업
#   KEEP_DAYS=14 backup-db.sh       # 보관 기간 바꾸기 (기본 7일)
#
# 복구는 restore-db.sh 로 한다. 백업 파일을 지금 DB에 그냥 부으면 "already exists" 오류만 나고 데이터는 돌아오지 않는다.
#
# 실패하면(DB가 꺼져 있는 등) 파일을 남기지 않고 실패로 끝난다. 빈 파일이 정상 백업처럼 남아
# 오래된 정상 백업만 지워지는 일을 막는다. 오래된 백업은 새 백업이 성공했을 때만 지운다.

set -euo pipefail

ROOT="${YEOGIROK_ROOT:-$HOME/yeogirok}"
INFRA="$ROOT/infra-src/infra"
OUT="$ROOT/backups"
KEEP_DAYS="${KEEP_DAYS:-7}"

mkdir -p "$OUT"
cd "$INFRA"

# 초까지 넣어 같은 분에 두 번 돌아도 앞 백업을 덮어쓰지 않는다.
FILE="$OUT/yeogirok-$(date +%F-%H%M%S).sql.gz"
TMP="$FILE.part"
trap 'rm -f "$TMP"' EXIT

fail() {
  echo "[backup] $(date '+%F %T') 실패: $1 이전 백업은 그대로 둡니다."
  exit 1
}

# DB 계정 정보는 컨테이너 안의 환경변수를 그대로 쓴다 (작은따옴표라 컨테이너 안에서 풀린다).
docker compose exec -T db sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB"' | gzip > "$TMP" \
  || fail "pg_dump 가 끝나지 않았습니다(DB 가 꺼져 있는지 docker compose ps db 로 확인)."

# pg_dump 는 끝까지 쓰면 마지막에 "dump complete" 줄을 남긴다. 없으면 중간에 끊긴 것이다.
tail_lines="$(gunzip -c "$TMP" | tail -n 20)" || fail "백업 파일을 읽지 못했습니다."
[[ "$tail_lines" == *"PostgreSQL database dump complete"* ]] || fail "백업이 끝까지 쓰이지 않았습니다."
mv "$TMP" "$FILE"

find "$OUT" -name 'yeogirok-*.sql.gz' -mtime +"$KEEP_DAYS" -delete
echo "[backup] $(date '+%F %T') $FILE ($(du -h "$FILE" | cut -f1))"
