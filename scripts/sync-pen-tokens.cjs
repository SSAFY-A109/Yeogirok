#!/usr/bin/env node
/**
 * design.pen(Pencil 문서 변수) → design/tokens.json 동기화
 *
 *   node scripts/sync-pen-tokens.cjs          Pencil 변수와 다른 토큰 값을 tokens.json에 반영하고 $source.pencilSyncedAt 갱신
 *   node scripts/sync-pen-tokens.cjs --check  불일치만 보고 (있으면 exit 1 — 훅·harness:check용)
 *
 * 디자인의 확정 원천은 Pencil(design.pen)이다. design.pen은 평문 JSON이라 파일을 직접 읽는다(MCP 불필요).
 *
 * 매핑 (Pencil 변수 이름 → tokens.json 경로):
 *   <시맨틱 색 이름>(테마 light/dark) → semantic.color.<이름>.$value.{light,dark}
 *   white · black                      → primitive.color.{white,black}
 *   radius-<k>                         → semantic.radius.<k>
 *   size-<k>                           → semantic.size.<k>
 *   font-sans                          → primitive.fontFamily.sans
 * 시맨틱 색은 같은 hex의 프리미티브가 있으면 {primitive.color.…} 참조로, 없으면 hex 그대로 기록한다.
 */
const fs = require("fs");
const path = require("path");

const ROOT = path.resolve(__dirname, "..");
const PEN_PATH = path.join(ROOT, "design.pen");
const TOKENS_PATH = path.join(ROOT, "design", "tokens.json");
const CHECK = process.argv.includes("--check");

const pen = JSON.parse(fs.readFileSync(PEN_PATH, "utf8"));
const tokens = JSON.parse(fs.readFileSync(TOKENS_PATH, "utf8"));
const MODES = tokens.$modes ?? ["light", "dark"];

function getByPath(obj, dotted) {
  return dotted.split(".").reduce((acc, key) => (acc == null ? undefined : acc[key]), obj);
}
function resolve(value, depth = 0) {
  if (depth > 10) throw new Error(`토큰 참조가 너무 깊습니다: ${JSON.stringify(value)}`);
  if (typeof value === "string") {
    const m = value.match(/^\{(.+)\}$/);
    if (!m) return value;
    const target = getByPath(tokens, m[1]);
    if (target === undefined) throw new Error(`참조를 찾을 수 없습니다: ${value}`);
    return resolve(target.$value !== undefined ? target.$value : target, depth + 1);
  }
  if (value && typeof value === "object" && !Array.isArray(value)) {
    const out = {};
    for (const [k, v] of Object.entries(value)) out[k] = resolve(v, depth + 1);
    return out;
  }
  return value;
}
function normalizeHex(hex) {
  if (typeof hex !== "string") return hex;
  let h = hex.trim().toUpperCase();
  if (!h.startsWith("#")) return h;
  if (h.length === 4) h = `#${h[1]}${h[1]}${h[2]}${h[2]}${h[3]}${h[3]}`;
  if (h.length === 9 && h.endsWith("FF")) h = h.slice(0, 7);
  return h;
}
// Pencil 변수 값 → { light, dark } (테마 없는 값은 두 모드에 동일)
function penModes(def) {
  if (!Array.isArray(def.value)) return Object.fromEntries(MODES.map((m) => [m, def.value]));
  const out = {};
  for (const m of MODES) {
    const hit = def.value.find((v) => v.theme && v.theme.mode === m) ?? def.value.find((v) => !v.theme);
    if (hit) out[m] = hit.value;
  }
  return out;
}
// hex → 같은 값을 가진 프리미티브 참조 (없으면 null)
const primitiveByHex = {};
(function walkPrimitive(group, prefix) {
  for (const [key, node] of Object.entries(group)) {
    if (key.startsWith("$")) continue;
    if (node && typeof node === "object" && "$value" in node) {
      if (typeof node.$value === "string") primitiveByHex[normalizeHex(node.$value)] ??= `{primitive.color.${[...prefix, key].join(".")}}`;
    } else if (node && typeof node === "object") walkPrimitive(node, [...prefix, key]);
  }
})(tokens.primitive.color, []);

const drift = []; // { path, current, pen, apply() }
const unmapped = []; // tokens.json에 대응이 없는 Pencil 변수
const missing = []; // Pencil에 없는 토큰

for (const [name, def] of Object.entries(pen.variables ?? {})) {
  if (def.type === "color") {
    if (name === "white" || name === "black") {
      const node = tokens.primitive.color[name];
      const penHex = normalizeHex(def.value);
      const cur = normalizeHex(resolve(node.$value));
      if (cur !== penHex) drift.push({ path: `primitive.color.${name}`, current: cur, pen: penHex, apply: () => (node.$value = penHex) });
      continue;
    }
    const node = tokens.semantic.color[name];
    if (!node || !("$value" in node)) {
      unmapped.push(`${name} (color)`);
      continue;
    }
    const modes = penModes(def);
    for (const m of MODES) {
      if (modes[m] === undefined) continue;
      const penHex = normalizeHex(modes[m]);
      const curRaw = typeof node.$value === "object" ? node.$value[m] : node.$value;
      const cur = normalizeHex(resolve(curRaw));
      if (cur !== penHex) {
        drift.push({
          path: `semantic.color.${name}.${m}`,
          current: cur,
          pen: penHex,
          apply: () => {
            if (typeof node.$value !== "object") node.$value = Object.fromEntries(MODES.map((mode) => [mode, node.$value]));
            node.$value[m] = primitiveByHex[penHex] ?? penHex;
          },
        });
      }
    }
  } else if (def.type === "number") {
    const group = name.startsWith("radius-") ? "radius" : name.startsWith("size-") ? "size" : null;
    if (!group) {
      unmapped.push(`${name} (number)`);
      continue;
    }
    const key = name.slice(group.length + 1);
    const node = tokens.semantic[group]?.[key];
    if (!node || !("$value" in node)) {
      unmapped.push(`${name} (number)`);
      continue;
    }
    const cur = resolve(node.$value);
    if (Number(cur) !== Number(def.value)) drift.push({ path: `semantic.${group}.${key}`, current: cur, pen: def.value, apply: () => (node.$value = def.value) });
  } else if (def.type === "string") {
    if (name !== "font-sans") {
      unmapped.push(`${name} (string)`);
      continue;
    }
    const node = tokens.primitive.fontFamily.sans;
    const cur = resolve(node.$value);
    if (cur !== def.value) drift.push({ path: "primitive.fontFamily.sans", current: cur, pen: def.value, apply: () => (node.$value = def.value) });
  }
}

for (const name of Object.keys(tokens.semantic.color)) {
  if (name.startsWith("$")) continue;
  if (!pen.variables?.[name]) missing.push(`semantic.color.${name}`);
}
for (const group of ["radius", "size"]) {
  for (const key of Object.keys(tokens.semantic[group] ?? {})) {
    if (key.startsWith("$")) continue;
    if (!pen.variables?.[`${group}-${key}`]) missing.push(`semantic.${group}.${key}`);
  }
}

const label = CHECK ? "검사" : "동기화";
if (unmapped.length) console.log(`  △ tokens.json에 대응 토큰이 없는 Pencil 변수 ${unmapped.length}개: ${unmapped.join(", ")}`);
// 코드 전용 토큰 목록은 매번 같은 긴 줄이라 검사 모드에서는 개수만 보인다(--verbose로 전체).
if (missing.length)
  console.log(
    CHECK && !process.argv.includes("--verbose")
      ? `  △ Pencil 변수가 없는 코드 전용 토큰 ${missing.length}개`
      : `  △ Pencil 변수가 없는 토큰 ${missing.length}개(코드 전용으로 간주): ${missing.join(", ")}`,
  );

if (drift.length === 0) {
  console.log(`design.pen ↔ tokens.json ${label}: 일치 (Pencil 변수 ${Object.keys(pen.variables ?? {}).length}개)`);
  process.exit(0);
}

for (const d of drift) console.log(`  ${CHECK ? "✗" : "→"} ${d.path}: ${JSON.stringify(d.current)} → ${JSON.stringify(d.pen)}`);
if (CHECK) {
  console.error(`design.pen ↔ tokens.json 불일치 ${drift.length}건 — Pencil이 원천이므로 \`pnpm tokens:sync\`로 반영한다`);
  process.exit(1);
}

for (const d of drift) d.apply();
tokens.$source ??= {};
tokens.$source.pencilFile = "design.pen";
tokens.$source.pencilSyncedAt = new Date().toISOString().slice(0, 10);
fs.writeFileSync(TOKENS_PATH, `${JSON.stringify(tokens, null, 2)}\n`);
console.log(`design.pen → tokens.json 반영 ${drift.length}건 ($source.pencilSyncedAt = ${tokens.$source.pencilSyncedAt}). 이어서 산출물을 재생성한다(sync-tokens.cjs).`);
