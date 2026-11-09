#!/usr/bin/env bash
# 서버에서 실행한다. backend/dev 최신 코드로 백엔드 컨테이너를 다시 만들고, 응답할 때까지 확인한다.
#
#   deploy-backend.sh                       # 브랜치 최신 커밋으로 바로 배포 (GitHub Actions가 SSH로 실행)
#   DEPLOY_SHA=<커밋> deploy-backend.sh     # 그 커밋으로 배포 (Jenkins가 테스트한 커밋을 넘긴다, 가이드 10단계)
#   deploy-backend.sh --if-changed          # 새 커밋이 있을 때만 배포 (cron 자동 배포, 가이드 6단계 플랜 B)
#   deploy-backend.sh --check               # 배포하지 않고, 새 코드가 요구하는 값이 compose·.env 에 다 있는지만 본다
#
# 실패해도 서비스가 최대한 살아 있도록 세 겹으로 막는다.
#   1. 배포 전 점검: 새 코드가 요구하는 값이 compose·.env 에 없으면 아무것도 바꾸지 않고 끝난다.
#   2. 빌드 실패: 기존 컨테이너가 그대로 남는다.
#   3. 자동 되돌리기: 새 컨테이너가 응답하지 않으면 로그를 보여 주고, 직전에 정상이던 이미지로 되돌린다.
#      코드만 되돌린다. DB 는 되돌리지 않는다(가이드 6단계 "배포 안전장치").
#
# 끝나는 코드 (Jenkins 가 Mattermost 알림 문구를 고르는 데 쓴다)
#   0 성공 · 1 실패(빌드 실패, 되돌리기 실패 등) · 2 배포 전 점검에서 멈춤(아무것도 안 바뀜) · 3 새 버전 실패, 이전 버전으로 되돌림

set -euo pipefail

ROOT="${YEOGIROK_ROOT:-$HOME/yeogirok}"
BRANCH="${BACKEND_BRANCH:-backend/dev}"
SRC="$ROOT/backend-src"
INFRA="$ROOT/infra-src/infra"
# 백엔드에 actuator가 들어오면 http://127.0.0.1:8080/actuator/health 로 바꾼다.
HEALTH_URL="${HEALTH_URL:-http://127.0.0.1:8080/v3/api-docs}"
IMAGE=yeogirok-backend                     # docker-compose.yml 의 backend image 이름
RESOURCES=backend/src/main/resources       # prod 프로필이 읽는 설정 파일 위치
MODE="${1:-}"

if [[ "$MODE" != "--check" ]]; then
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
fi

cd "$SRC"
git fetch --quiet origin "$BRANCH"
# DEPLOY_SHA 가 없으면 방금 받은 브랜치 최신 커밋을 배포한다.
NEW="$(git rev-parse --verify "${DEPLOY_SHA:-FETCH_HEAD}^{commit}")"
OLD="$(git rev-parse HEAD)"

if [[ "$MODE" == "--if-changed" && "$NEW" == "$OLD" ]]; then
  exit 0
fi

cd "$INFRA"

# ── 1. 배포 전 점검 ─────────────────────────────────────────────
# 새 커밋의 설정 파일을 꺼내, 기본값 없이 요구하는 값을 compose 가 .env 까지 채워 넘기는지 본다.
# 아직 코드를 바꾸지 않았으므로, 여기서 멈추면 지금 떠 있는 백엔드는 그대로다.
check_env() {
  local tmp rc=0 f
  tmp="$(mktemp -d)"
  for f in application.yaml application.yml application-prod.yaml application-prod.yml; do
    git -C "$SRC" show "$NEW:$RESOURCES/$f" > "$tmp/$f" 2>/dev/null || rm -f "$tmp/$f"
  done
  if compgen -G "$tmp/*" >/dev/null; then
    bash "$INFRA/scripts/check-backend-env.sh" --compose "$INFRA/docker-compose.yml" "$tmp"/* || rc=$?
  else
    echo "[check] $RESOURCES 에 설정 파일이 없습니다"
    rc=2
  fi
  rm -rf "$tmp"
  return "$rc"
}

echo "[deploy] 배포 전 점검: ${NEW:0:7} 이 요구하는 값"
rc=0
check_env || rc=$?
if [[ "$MODE" == "--check" ]]; then
  exit "$rc"
fi
if [[ $rc -eq 1 ]]; then
  echo "[deploy] 중단: 배포하지 않았습니다. 지금 떠 있는 백엔드는 그대로입니다."
  exit 2
elif [[ $rc -ne 0 ]]; then
  # 점검 도구가 없는 등 점검 자체를 못 한 경우. 배포를 막지는 않는다.
  echo "[deploy] 경고: 배포 전 점검을 하지 못해 건너뜁니다"
fi

# ── 2. 되돌릴 이미지 표시 ───────────────────────────────────────
# 지금 백엔드가 정상 응답하면 그 이미지에 :previous 표시를 붙여 둔다. 새 버전이 실패하면 여기로 되돌린다.
# 지금 백엔드가 죽어 있으면 표시를 옮기지 않는다. 예전에 표시해 둔 정상 이미지가 있으면 그것을 쓴다.
if curl -fsS -o /dev/null --max-time 5 "$HEALTH_URL"; then
  cid="$(docker compose ps -q backend)"
  [[ -n "$cid" ]] && docker tag "$(docker inspect -f '{{.Image}}' "$cid")" "$IMAGE:previous"
fi

# 이미지를 만든 커밋(빌드 때 붙인 yeogirok.commit 표시). 예전 이미지처럼 표시가 없으면 '커밋 모름'.
image_commit() {
  local c
  c="$(docker image inspect -f '{{index .Config.Labels "yeogirok.commit"}}' "$1" 2>/dev/null || true)"
  [[ -z "$c" || "$c" == "<no value>" || "$c" == unknown ]] && c="커밋 모름"
  echo "${c:0:7}"
}

# ── 3. 빌드 ─────────────────────────────────────────────────────
git -C "$SRC" reset --quiet --hard "$NEW"
echo "[deploy] $(date '+%F %T') ${OLD:0:7} -> ${NEW:0:7} 배포 시작"
# BACKEND_COMMIT: compose 가 이미지에 yeogirok.commit 표시로 붙인다.
if ! BACKEND_COMMIT="$NEW" docker compose build backend; then
  echo "[deploy] 실패: 빌드하지 못했습니다. 기존 백엔드는 그대로입니다."
  exit 1
fi

# ── 4. 교체와 확인 ──────────────────────────────────────────────
# 응답하면 0. 컨테이너가 시작하다 멈추기를 반복하거나 $1초가 지나면 1.
wait_healthy() {
  local deadline=$((SECONDS + $1)) cid restarts
  while ((SECONDS < deadline)); do
    curl -fsS -o /dev/null --max-time 5 "$HEALTH_URL" 2>/dev/null && return 0
    cid="$(docker compose ps -q backend)"
    restarts="$( [[ -n "$cid" ]] && docker inspect -f '{{.RestartCount}}' "$cid" 2>/dev/null || echo 0)"
    if ((restarts >= 2)); then
      echo "[deploy] 백엔드가 시작하다 멈추기를 반복합니다 (재시작 ${restarts}번)"
      return 1
    fi
    sleep 3
  done
  echo "[deploy] $1초 안에 응답하지 않았습니다"
  return 1
}

if docker compose up -d backend && wait_healthy 120; then
  echo "[deploy] 성공: $(git -C "$SRC" log -1 --format='%h %s')"
  docker image prune -f >/dev/null   # 이전 빌드에서 남은 이미지 정리 (:previous 는 남는다)
  exit 0
fi

echo "[deploy] 실패: 새 백엔드(${NEW:0:7})가 뜨지 않았습니다. 최근 로그:"
docker compose logs --tail 80 backend

# ── 5. 자동 되돌리기 ────────────────────────────────────────────
if ! docker image inspect "$IMAGE:previous" >/dev/null 2>&1; then
  echo "[deploy] 되돌릴 정상 이미지가 없습니다(첫 배포이거나, 배포 전부터 백엔드가 멈춰 있었음). 백엔드가 멈춰 있습니다."
  exit 1
fi

prev="$(image_commit "$IMAGE:previous")"
echo "[deploy] 직전에 정상이던 이미지(${prev})로 되돌립니다"
docker tag "$IMAGE:previous" "$IMAGE:latest"
if docker compose up -d --no-deps --force-recreate backend && wait_healthy 120; then
  docker image prune -f >/dev/null   # 실패한 새 이미지 정리
  echo "[deploy] 되돌리기 성공: 서비스는 이전 버전(${prev})으로 돌고 있습니다. ${NEW:0:7} 은 배포되지 않았습니다."
  exit 3
fi

echo "[deploy] 되돌리기도 실패했습니다. 백엔드가 멈춰 있습니다. 최근 로그:"
docker compose logs --tail 40 backend
exit 1
