#!/usr/bin/env node
/**
 * UI 코드가 디자인 토큰만 사용하는지 검사한다.
 *
 *   node scripts/check-tokens.cjs
 *
 * 검사 대상: app/, components/, features/, lib/ (lib/theme.ts 제외)의 .ts/.tsx
 * 위반:
 *   1. 원시 색상값(hex, rgb/rgba/hsl)
 *   2. className 안의 arbitrary value (예: p-[13px], bg-[#fff])
 *   3. 프리미티브 팔레트 클래스 (bg-slate-100, text-blue-600 …) — 테마에 없어 무시되므로 오류
 *   4. StyleSheet.create
 * 예외: 첫 줄이 "// vendor: react-native-reusables"인 벤더 파일은 2, 3을 검사하지 않는다 (원본 유지 목적). 1, 4는 검사한다.
 */
const fs = require("fs");
const path = require("path");

const ROOT = path.resolve(__dirname, "..");
const DIRS = ["app", "components", "features", "lib"];
const EXCLUDE = new Set([path.join(ROOT, "lib", "theme.ts")]);
const VENDOR_MARK = /^\/\/ vendor: react-native-reusables/;

// white/black은 모드 무관 고정 토큰으로 테마에 노출되므로 허용한다 (RNR 컴포넌트가 사용).
const PALETTES =
  "slate|gray|zinc|neutral|stone|red|orange|amber|yellow|lime|green|emerald|teal|cyan|sky|blue|indigo|violet|purple|fuchsia|pink|rose";
const RULES = [
  { id: "raw-color", re: /#[0-9a-fA-F]{3,8}\b|\b(?:rgba?|hsla?)\(/g, msg: "원시 색상값 대신 시맨틱 토큰을 사용하세요" },
  { id: "arbitrary-value", re: /\b[\w:-]+-\[[^\]]+\]/g, msg: "arbitrary value 대신 토큰을 사용하세요", vendorExempt: true },
  {
    id: "primitive-palette",
    re: new RegExp(`\\b(?:bg|text|border|fill|stroke|from|to|via|ring|shadow|divide|outline|accent|caret|decoration|placeholder)-(?:${PALETTES})(?:-\\d{2,3})?\\b`, "g"),
    msg: "프리미티브 팔레트 대신 시맨틱 토큰(bg-card, text-foreground, text-destructive …)을 사용하세요",
    vendorExempt: true,
  },
  { id: "stylesheet-create", re: /StyleSheet\.create\(/g, msg: "StyleSheet.create 대신 NativeWind className을 사용하세요" },
];

function listFiles(dir) {
  if (!fs.existsSync(dir)) return [];
  return fs.readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
    const abs = path.join(dir, entry.name);
    if (entry.isDirectory()) return entry.name === "node_modules" ? [] : listFiles(abs);
    return /\.(ts|tsx)$/.test(entry.name) && !entry.name.endsWith(".d.ts") ? [abs] : [];
  });
}

const files = DIRS.flatMap((d) => listFiles(path.join(ROOT, d))).filter((f) => !EXCLUDE.has(f));
const violations = [];

for (const file of files) {
  const lines = fs.readFileSync(file, "utf8").split("\n");
  const isVendor = VENDOR_MARK.test(lines[0] ?? "");
  lines.forEach((line, i) => {
    const trimmed = line.trim();
    if (trimmed.startsWith("//") || trimmed.startsWith("*")) return;
    for (const rule of RULES) {
      if (rule.vendorExempt && isVendor) continue;
      rule.re.lastIndex = 0;
      const m = rule.re.exec(line);
      if (m) violations.push({ file: path.relative(ROOT, file), line: i + 1, id: rule.id, match: m[0], msg: rule.msg });
    }
  });
}

if (violations.length) {
  for (const v of violations) console.error(`${v.file}:${v.line}  [${v.id}] ${v.match}  — ${v.msg}`);
  console.error(`\n토큰 위반 ${violations.length}건`);
  process.exit(1);
}
console.log(`토큰 검사 통과 (${files.length}개 파일)`);
