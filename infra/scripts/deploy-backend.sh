#!/usr/bin/env bash
# 서버에서 실행한다. backend/dev 최신 코드로 백엔드 컨테이너를 다시 만들고, 응답할 때까지 확인한다.
#
#   deploy-backend.sh                       # 브랜치 최신 커밋으로 바로 배포 (GitHub Actions가 SSH로 실행)
#   DEPLOY_SHA=<커밋> deploy-backend.sh     # 그 커밋으로 배포 (Jenkins가 테스트한 커밋을 넘긴다, 가이드 10단계)
#   deploy-backend.sh --if-changed          # 새 커밋이 있을 때만 배포 (cron 자동 배포, 가이드 6단계 플랜 B)
#
# 빌드가 실패하면 기존 컨테이너가 그대로 남는다. 새 컨테이너가 뜬 뒤 응답이 없으면 로그를 보여주고 실패로 끝난다.

set -euo pipefail

ROOT="${YEOGIROK_ROOT:-$HOME/yeogirok}"
BRANCH="${BACKEND_BRANCH:-backend/dev}"
SRC="$ROOT/backend-src"
INFRA="$ROOT/infra-src/infra"
# 백엔드에 actuator가 들어오면 http://127.0.0.1:8080/actuator/health 로 바꾼다.
HEALTH_URL="${HEALTH_URL:-http://127.0.0.1:8080/v3/api-docs}"

# 배포가 동시에 두 번 돌지 않게 잠근다.
# 커밋을 지정한 배포(Jenkins)는 앞 배포가 끝날 때까지 기다린다. 건너뛰면 배포하지 않은 커밋을 성공으로 알리게 된다.
exec 9>"/tmp/yeogirok-deploy-backend.lock"
if [[ -n "${DEPLOY_SHA:-}" ]]; then
  if ! flock -n 9; then
    echo "[deploy] 다른 배포가 진행 중이라 끝날 때까지 기다립니다 (최대 10분)"
    flock -w 600 9 || { echo "[deploy] 실패: 10분이 지나도 앞 배포가 끝나지 않았습니다"; exit 1; }
  fi
elif ! flock -n 9; then
  echo "[deploy] 다른 배포가 진행 중이라 건너뜁니다"
  exit 0
fi

cd "$SRC"
git fetch --quiet origin "$BRANCH"
# DEPLOY_SHA 가 없으면 방금 받은 브랜치 최신 커밋을 배포한다.
NEW="$(git rev-parse --verify "${DEPLOY_SHA:-FETCH_HEAD}^{commit}")"
OLD="$(git rev-parse HEAD)"

if [[ "${1:-}" == "--if-changed" && "$NEW" == "$OLD" ]]; then
  exit 0
fi

git reset --quiet --hard "$NEW"
echo "[deploy] $(date '+%F %T') ${OLD:0:7} -> ${NEW:0:7} 배포 시작"

cd "$INFRA"
docker compose up -d --build backend

for _ in $(seq 1 40); do
  if curl -fsS -o /dev/null "$HEALTH_URL"; then
    echo "[deploy] 성공: $(git -C "$SRC" log -1 --format='%h %s')"
    docker image prune -f >/dev/null   # 이전 빌드에서 남은 이미지 정리
    exit 0
  fi
  sleep 3
done

echo "[deploy] 실패: 120초 안에 백엔드가 응답하지 않았습니다. 최근 로그:"
docker compose logs --tail 80 backend
exit 1
