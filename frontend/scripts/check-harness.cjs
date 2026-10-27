#!/usr/bin/env node
// 하네스 무결성 검사: 미러 동기화, JSON 유효성, 스킬 잠금, Pencil↔토큰, 토큰 산출물, 규칙 frontmatter, 이미지 참조
//
//   node scripts/check-harness.cjs            통과하면 한 줄, 실패하면 실패한 항목의 내용만 출력 (에이전트 토큰 절약)
//   node scripts/check-harness.cjs --verbose  항목별 결과를 모두 출력
//
// bash 대신 node로 실행한다. Windows에서 WSL이 설치돼 있으면 pnpm이 띄우는 bash가
// WSL로 잡혀 node를 찾지 못하므로, 셸 의존 없이 어떤 OS에서도 동작하게 한다.
const fs = require("fs");
const path = require("path");
const { spawnSync } = require("child_process");

process.chdir(path.join(__dirname, ".."));
const VERBOSE = process.argv.includes("--verbose");

// ---------- 결과 수집 ----------
// 항목마다 출력 줄을 모아 두었다가, 실패한 항목(또는 --verbose)만 출력한다.
const sections = [];
let current = null;
function section(title) {
  current = { title, lines: [], failed: false };
  sections.push(current);
}
function log(line) {
  current.lines.push(line);
}
function fail(line) {
  current.failed = true;
  if (line) current.lines.push(line);
}

/** 디렉터리 아래 모든 파일의 상대 경로 목록 (정렬) */
function listFiles(dir) {
  const out = [];
  const walk = (cur) => {
    for (const ent of fs.readdirSync(cur, { withFileTypes: true })) {
      const full = path.join(cur, ent.name);
      if (ent.isDirectory()) walk(full);
      else out.push(path.relative(dir, full).split(path.sep).join("/"));
    }
  };
  if (fs.existsSync(dir)) walk(dir);
  return out.sort();
}

/** diff -rq 와 같은 결과: 한쪽에만 있거나 내용이 다른 파일 목록 */
function diffDirs(a, b) {
  const fa = listFiles(a);
  const fb = listFiles(b);
  const sb = new Set(fb);
  const problems = [];
  for (const f of fa) {
    if (!sb.has(f)) problems.push(`Only in ${a}: ${f}`);
    else if (!fs.readFileSync(path.join(a, f)).equals(fs.readFileSync(path.join(b, f))))
      problems.push(`Files ${a}/${f} and ${b}/${f} differ`);
  }
  const sa = new Set(fa);
  for (const f of fb) if (!sa.has(f)) problems.push(`Only in ${b}: ${f}`);
  return problems;
}

function checkMirror(src, dst, limit) {
  section(`${src} ↔ ${dst}`);
  const problems = diffDirs(src, dst);
  if (problems.length === 0) log("  OK");
  else {
    fail("  FAIL: 미러 불일치 (pnpm harness:sync)");
    for (const p of problems.slice(0, limit)) log(`  ${p}`);
    if (problems.length > limit) log(`  … 외 ${problems.length - limit}건`);
  }
}

/** node 하위 스크립트를 실행해 출력을 모으고 종료 코드로 성공 여부를 돌려준다 */
function runNode(args) {
  const r = spawnSync(process.execPath, args, { encoding: "utf8" });
  for (const line of `${r.stdout ?? ""}${r.stderr ?? ""}`.split(/\r?\n/)) if (line.trim()) log(`  ${line}`);
  return r.status === 0;
}

// ---------- 검사 ----------
checkMirror(".agents/rules", ".claude/rules", 30);
checkMirror(".agents/skills", ".claude/skills", 20);

section("design/*.json 파싱");
for (const f of ["design/tokens.json", "design/design-map.json"]) {
  if (!fs.existsSync(f)) {
    fail(`  FAIL: ${f} 없음`);
    continue;
  }
  try {
    JSON.parse(fs.readFileSync(f, "utf8"));
    log(`  OK ${f}`);
  } catch {
    fail(`  FAIL: ${f} 파싱 오류`);
  }
}

section("design.pen ↔ tokens.json (Pencil 변수가 원천)");
if (!runNode(["scripts/sync-pen-tokens.cjs", "--check"])) fail();

section("skills-lock.json 이 모든 스킬을 추적하는지");
{
  let lock = {};
  try {
    lock = JSON.parse(fs.readFileSync("skills-lock.json", "utf8"));
  } catch {
    fail("  FAIL: skills-lock.json 파싱 오류");
  }
  const skills = fs
    .readdirSync(".agents/skills", { withFileTypes: true })
    .filter((e) => e.isDirectory())
    .map((e) => e.name)
    .sort();
  for (const name of skills) {
    if (lock.skills && lock.skills[name]) log(`  OK ${name}`);
    else fail(`  FAIL: skills-lock.json 에 ${name} 없음`);
  }
  for (const name of Object.keys(lock.skills ?? {})) {
    if (!skills.includes(name)) fail(`  FAIL: skills-lock.json 에만 있는 스킬 ${name} (폴더 없음)`);
  }
}

section("토큰 산출물 최신 여부");
if (!runNode(["scripts/sync-tokens.cjs", "--check"])) fail();

section("규칙 파일 frontmatter(paths)");
{
  const rules = fs
    .readdirSync(".agents/rules")
    .filter((f) => f.endsWith(".md"))
    .sort();
  for (const f of rules) {
    const rel = `.agents/rules/${f}`;
    // CRLF 체크아웃도 허용하도록 \r 은 제거하고 비교한다
    const lines = fs.readFileSync(rel, "utf8").split(/\r?\n/);
    const hasFrontmatter = lines[0] === "---";
    const hasPaths = lines.some((l) => l.startsWith("paths:"));
    if (!hasFrontmatter || !hasPaths) fail(`  FAIL: ${rel} 에 paths frontmatter 없음`);
  }
  if (!current.failed) log(`  OK 규칙 ${rules.length}개`);
}

section("design.pen 이미지 참조 ↔ design/ 이미지 파일");
{
  // .pen 파일의 image fill url은 그 .pen 파일 기준 상대경로다.
  // (1) url 없는 image fill = 붙여넣기 때 이미지가 안 들어온 것, (2) 파일이 없는 url,
  // (3) 어떤 .pen도 참조하지 않는 파일 = 고아 파일이 쌓이는 것을 막는다.
  const IMAGE_EXT = /\.(png|jpe?g|webp|gif|svg)$/i;
  // 이미지가 놓일 수 있는 폴더 — 2026-10-27 이전에는 design/images만 검사해 실제 자산 폴더(design/figma-assets)의 고아 파일을 놓쳤다.
  const IMAGE_DIRS = ["design/images", "design/figma-assets", "design/assets"];
  const penFiles = ["design.pen"];
  if (fs.existsSync("design/pencil"))
    for (const f of fs.readdirSync("design/pencil")) if (f.endsWith(".pen")) penFiles.push(`design/pencil/${f}`);
  const referenced = new Set();
  const noUrl = [];
  const missing = [];
  for (const pen of penFiles) {
    let doc;
    try {
      doc = JSON.parse(fs.readFileSync(pen, "utf8"));
    } catch {
      fail(`  FAIL: ${pen} 파싱 오류`);
      continue;
    }
    const penDir = path.dirname(pen);
    const walk = (n, chain) => {
      if (!n || typeof n !== "object") return;
      for (const f of [].concat(n.fill || [])) {
        if (!f || typeof f !== "object" || f.type !== "image") continue;
        if (!f.url) {
          noUrl.push(`${pen}: ${chain.join(" > ")} (${n.id})`);
          continue;
        }
        if (/^(https?:|data:)/.test(f.url)) continue;
        const rel = path.normalize(path.join(penDir, f.url)).split(path.sep).join("/");
        referenced.add(rel);
        if (!fs.existsSync(rel)) missing.push(`${pen}: ${f.url} (${n.id})`);
      }
      for (const c of n.children || []) walk(c, [...chain, c.name || c.id]);
    };
    walk({ children: doc.children }, []);
  }
  const orphans = IMAGE_DIRS.flatMap((dir) =>
    listFiles(dir)
      .filter((f) => IMAGE_EXT.test(f))
      .map((f) => `${dir}/${f}`)
      .filter((f) => !referenced.has(f)),
  );
  if (noUrl.length) {
    fail(`  FAIL: url 없는 image fill ${noUrl.length}개 (이미지가 들어오지 않은 노드)`);
    for (const s of noUrl.slice(0, 10)) log(`    ${s}`);
  }
  if (missing.length) {
    fail(`  FAIL: 파일이 없는 이미지 참조 ${missing.length}개`);
    for (const s of missing.slice(0, 10)) log(`    ${s}`);
  }
  if (orphans.length) {
    fail(`  FAIL: 어떤 .pen도 참조하지 않는 이미지 ${orphans.length}개 — 삭제하거나 참조를 연결한다`);
    for (const s of orphans.slice(0, 10)) log(`    ${s}`);
  }
  if (!noUrl.length && !missing.length && !orphans.length) log(`  OK 참조 ${referenced.size}개 파일, 고아 파일 없음`);
}

// ---------- 출력 ----------
const failed = sections.filter((s) => s.failed);
if (VERBOSE || failed.length) {
  for (const s of VERBOSE ? sections : failed) {
    console.log(`▶ ${s.title}${s.failed ? "  ✗" : ""}`);
    for (const l of s.lines) console.log(l);
  }
  console.log();
}
if (failed.length) {
  console.log(`하네스 검사 실패: ${failed.length}/${sections.length}개 항목 (${failed.map((s) => s.title).join(" · ")})`);
  process.exit(1);
}
console.log(`하네스 검사 통과 (${sections.length}개 항목)`);
