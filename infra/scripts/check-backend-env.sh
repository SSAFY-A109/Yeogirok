#!/usr/bin/env bash
# 백엔드 설정 파일이 기본값 없이 요구하는 값(${KEY})을 서버 compose 가 다 넘기는지 확인한다.
# 11/4(Redis)·11/6(여행방)처럼 값이 빠진 채 배포돼 백엔드가 멈추는 일을 배포 전에 막는다.
#
#   check-backend-env.sh <application.yaml> [<application-prod.yaml> ...]
#       서버용. .env 까지 채운 compose 값으로 본다. 값이 없거나 비어 있으면 실패.
#       deploy-backend.sh 가 배포 전에 부른다. 손으로 볼 때는 deploy-backend.sh --check 를 쓴다.
#
#   check-backend-env.sh --keys-only --compose <compose 파일> <application.yaml> ...
#       CI 용. .env 없이 compose 에 값 이름이 있는지만 본다(백엔드 PR 검사가 부른다).
#
# 기본값이 있는 값(${KEY:기본값}, ${KEY:})과 소문자 속성 참조(${spring.x})는 보지 않는다.
# 끝나는 코드: 0 모두 있음, 1 빠진 값 있음, 2 사용법·도구 오류

set -euo pipefail

KEYS_ONLY=0
COMPOSE="$(cd "$(dirname "$0")/.." && pwd)/docker-compose.yml"
SERVICE=backend

while [[ $# -gt 0 ]]; do
  case "$1" in
    --keys-only) KEYS_ONLY=1; shift ;;
    --compose) COMPOSE="$2"; shift 2 ;;
    --service) SERVICE="$2"; shift 2 ;;
    -h|--help) sed -n '2,13p' "$0"; exit 0 ;;
    --*) echo "[check] 모르는 옵션: $1" >&2; exit 2 ;;
    *) break ;;
  esac
done

if [[ $# -eq 0 ]]; then
  echo "[check] 사용법: check-backend-env.sh [--keys-only] [--compose <파일>] <application.yaml> ..." >&2
  exit 2
fi
command -v python3 >/dev/null || { echo "[check] python3 가 없어 확인할 수 없습니다" >&2; exit 2; }

# --keys-only 는 .env 를 읽지 않는다(--no-interpolate). ${REDIS_PASSWORD:?...} 같은 필수 표시도 그대로 둔다.
config_args=(config --format json)
[[ $KEYS_ONLY -eq 1 ]] && config_args+=(--no-interpolate)

err="$(mktemp)"
trap 'rm -f "$err"' EXIT
if ! COMPOSE_JSON="$(docker compose -f "$COMPOSE" "${config_args[@]}" 2>"$err")"; then
  echo "[check] 실패: docker compose 가 설정을 읽지 못했습니다 ($COMPOSE)"
  awk '!seen[$0]++ { print "  " $0 }' "$err"   # compose 가 같은 경고를 여러 번 찍어서 한 번씩만 보여 준다
  exit 1
fi
export COMPOSE_JSON

python3 - "$KEYS_ONLY" "$SERVICE" "$@" <<'PY'
import json, os, re, sys

keys_only = sys.argv[1] == "1"
service = sys.argv[2]
files = sys.argv[3:]

services = json.loads(os.environ["COMPOSE_JSON"]).get("services", {})
if service not in services:
    print(f"[check] 실패: compose 에 {service} 서비스가 없습니다")
    sys.exit(2)
env = services[service].get("environment") or {}
if isinstance(env, list):  # "KEY=값" 목록 형식도 받는다
    env = dict((e.split("=", 1) + [None])[:2] for e in env)

placeholder = re.compile(r"\$\{([A-Z][A-Z0-9_]*)\}")   # 기본값(:)이 없는 대문자 이름만
comment = re.compile(r"(^|\s)#.*$")                    # YAML 주석(줄 처음 또는 공백 뒤의 #)

required = {}  # 값 이름 -> 처음 나온 (파일, 줄)
for path in files:
    with open(path, encoding="utf-8") as f:
        for no, line in enumerate(f, 1):
            for m in placeholder.finditer(comment.sub("", line.rstrip("\r\n"))):
                required.setdefault(m.group(1), (path, no))

not_in_compose = sorted(k for k in required if k not in env)
empty = [] if keys_only else sorted(
    k for k in required if k in env and (env[k] is None or str(env[k]).strip() == ""))

where = ".env 없이 이름만" if keys_only else ".env 값까지"
print(f"[check] 백엔드 설정이 기본값 없이 요구하는 값 {len(required)}개를 compose({service})와 비교했습니다 ({where}).")
if not not_in_compose and not empty:
    print(f"[check] 통과: {len(required)}개 모두 있습니다.")
    sys.exit(0)

print(f"[check] 실패: {len(not_in_compose) + len(empty)}개가 없습니다.")
for k in not_in_compose:
    p, no = required[k]
    print(f"  - {k}: compose 가 넘기지 않음 ({os.path.basename(p)}:{no})")
for k in empty:
    p, no = required[k]
    print(f"  - {k}: .env 에 없거나 값이 비어 있음 ({os.path.basename(p)}:{no})")

print("[check] 해결:")
if not_in_compose and keys_only:
    print("  - infra/docker-compose.yml 의 backend environment 에 값을 추가하는 infra PR 이 먼저 머지돼야 합니다.")
    print("    infra 담당에게 알리고, infra PR 이 머지되면 이 검사를 다시 실행합니다(Re-run jobs).")
elif not_in_compose:
    print("  - compose 가 넘기지 않음: 서버 infra 코드가 최신인지 확인합니다(git -C ~/yeogirok/infra-src pull).")
    print("    최신인데도 없으면 infra/docker-compose.yml 에 추가하는 infra PR 이 필요합니다(가이드 9단계 '외부 키 추가하는 법').")
if empty:
    print("  - .env 에 없거나 값이 비어 있음: 서버 infra/.env 에 KEY=값 을 넣습니다. 만드는 법은 .env.example 에 있습니다.")

# GitHub Actions 에서는 PR 화면의 파일 줄에 오류 표시를 남긴다.
if os.environ.get("GITHUB_ACTIONS") == "true":
    for k in not_in_compose:
        p, no = required[k]
        print(f"::error file={p},line={no}::{k} 를 서버 compose(infra/dev)가 넘기지 않아 머지하면 서버 백엔드가 뜨지 못합니다. infra PR 이 먼저 필요합니다.")
sys.exit(1)
PY
