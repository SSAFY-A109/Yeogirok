#!/usr/bin/env node
// .agents/ (정본) → .claude/ (Claude Code 미러) 동기화
// rsync -a --delete 와 같은 결과: 대상 디렉터리를 지우고 전체를 다시 복사한다.
// bash/rsync 의존 없이 node만으로 동작하므로 OS에 관계없이 같은 결과를 낸다.
const fs = require("fs");
const path = require("path");

process.chdir(path.join(__dirname, ".."));
fs.mkdirSync(".claude", { recursive: true });

for (const d of ["rules", "skills"]) {
  fs.rmSync(path.join(".claude", d), { recursive: true, force: true });
  fs.cpSync(path.join(".agents", d), path.join(".claude", d), { recursive: true });
}

console.log("미러 동기화 완료: .claude/rules, .claude/skills");
