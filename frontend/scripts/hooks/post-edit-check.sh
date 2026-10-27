#!/usr/bin/env bash
# Claude Code PostToolUse 훅 (Edit|Write). stdin으로 훅 JSON을 받는다.
# - app/ components/ features/ lib/ 의 .ts/.tsx 편집 → 토큰 검사. 위반 시 exit 2 로 Claude에게 오류를 돌려준다.
# - design/tokens.json 편집 → 산출물 재생성 (tailwind.config.js, global.css, lib/theme.ts)
# - .agents/ 편집 → .claude/ 미러 동기화
set -u
cd "$(dirname "$0")/../.."

FILE=$(node -e '
let s="";process.stdin.on("data",d=>s+=d).on("end",()=>{
  try{const j=JSON.parse(s);process.stdout.write(j.tool_input?.file_path||j.tool_response?.filePath||"")}catch{process.stdout.write("")}
})')
[ -z "$FILE" ] && exit 0
REL=${FILE#"$PWD/"}

case "$REL" in
  design/tokens.json)
    OUT=$(node scripts/sync-tokens.cjs 2>&1) || { echo "tokens.json 생성 실패:"$'\n'"$OUT" >&2; exit 2; }
    echo "tokens.json 변경 → 산출물 재생성 완료 (tailwind.config.js, global.css, lib/theme.ts)"
    ;;
  .agents/*)
    node scripts/sync-harness.cjs >/dev/null && echo ".agents 변경 → .claude 미러 동기화 완료"
    ;;
  app/*.ts|app/*.tsx|components/*.ts|components/*.tsx|features/*.ts|features/*.tsx|lib/*.ts|lib/*.tsx)
    [ "$REL" = "lib/theme.ts" ] && exit 0
    OUT=$(node scripts/check-tokens.cjs 2>&1) || { echo "[tokens:check 실패] 규칙 70 위반 — 시맨틱 토큰으로 수정하세요:"$'\n'"$OUT" >&2; exit 2; }
    ;;
esac
exit 0
