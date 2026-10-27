#!/usr/bin/env node
// harness-backup.cjs 가 만든 사본을 저장소로 되돌린다.
// 브랜치를 옮겼다 오거나 pull 뒤에 .agents/·docs/·design.pen 이 사라졌을 때 쓴다.
// --prev 로 직전 세대를, --dry 로 무엇이 바뀌는지만 확인할 수 있다.
const fs = require("fs");
const os = require("os");
const path = require("path");

process.chdir(path.join(__dirname, ".."));

// harness-backup.cjs 와 같은 위치 규칙을 쓴다.
const PROJECT = `${path.basename(path.dirname(process.cwd()))}-${path.basename(process.cwd())}`;
const ROOT = process.env.HARNESS_BACKUP_DIR || path.join(os.homedir(), "harness-backup", PROJECT);
const SRC = path.join(ROOT, process.argv.includes("--prev") ? "prev" : "current");
const DRY = process.argv.includes("--dry");

if (!fs.existsSync(SRC)) {
  console.error("백업이 없다: " + SRC);
  console.error("먼저 `pnpm harness:backup` 을 돌려야 한다.");
  process.exit(1);
}

const TARGETS = fs.readdirSync(SRC).filter((f) => f !== "BACKUP-INFO.txt");
const added = [];
const changed = [];

for (const t of TARGETS) {
  for (const rel of relFiles(path.join(SRC, t), t)) {
    const from = path.join(SRC, rel);
    const to = path.join(process.cwd(), rel);
    if (!fs.existsSync(to)) added.push(rel);
    else if (!fs.readFileSync(from).equals(fs.readFileSync(to))) changed.push(rel);
    else continue;
    if (DRY) continue;
    fs.mkdirSync(path.dirname(to), { recursive: true });
    fs.copyFileSync(from, to);
  }
}

const info = path.join(SRC, "BACKUP-INFO.txt");
if (fs.existsSync(info)) console.log(fs.readFileSync(info, "utf8").split("\n")[1]);

if (!added.length && !changed.length) {
  console.log("되돌릴 것 없음 — 저장소가 백업과 같다.");
} else {
  console.log(`복구: 새로 만든 파일 ${added.length}개 / 내용이 달라 덮어쓴 파일 ${changed.length}개`);
  for (const f of changed.slice(0, 20)) console.log("  덮어씀 " + f);
  if (changed.length > 20) console.log(`  … 외 ${changed.length - 20}개`);
  if (DRY) console.log("[dry-run] 실제로는 쓰지 않았다.");
  else console.log("`pnpm harness:sync && pnpm harness:check` 로 미러를 맞추고 확인한다.");
}

// 백업에는 있는데 저장소에 없는 파일만 지우지 않고 남긴다(의도적으로 지운 파일을 되살리지 않기 위해).
function* relFiles(abs, rel) {
  const st = fs.statSync(abs);
  if (st.isFile()) {
    yield rel;
    return;
  }
  for (const e of fs.readdirSync(abs)) yield* relFiles(path.join(abs, e), path.join(rel, e));
}
