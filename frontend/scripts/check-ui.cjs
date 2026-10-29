const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const Module = require("node:module");
const ts = require("typescript");

const filename = path.resolve(__dirname, "../lib/utils.ts");
const compiled = ts.transpileModule(fs.readFileSync(filename, "utf8"), {
  compilerOptions: { module: ts.ModuleKind.CommonJS },
}).outputText;
const loaded = new Module(filename, module);
loaded.filename = filename;
loaded.paths = Module._nodeModulePaths(path.dirname(filename));
loaded._compile(compiled, filename);
const { cn } = loaded.exports;

assert.equal(cn("text-body text-foreground", "text-button text-primary-foreground"), "text-button text-primary-foreground");
assert.equal(cn("text-body-sm text-muted-foreground", "text-label"), "text-muted-foreground text-label");
assert.equal(cn("bg-card border-input", false, "bg-primary border-ring"), "bg-primary border-ring");
assert.equal(cn("h-input", "h-36"), "h-36");
assert.equal(cn("min-h-button-md", "min-h-button-lg"), "min-h-button-lg");
assert.equal(cn("rounded-md", "rounded-sm"), "rounded-sm");

const assetDirectory = path.resolve(__dirname, "../assets/ui");
for (const file of fs.readdirSync(assetDirectory)) {
  assert.ok(fs.statSync(path.join(assetDirectory, file)).size > 0, `Empty UI asset: ${file}`);
}
console.log("UI 검사 통과 (클래스 충돌 6건, 로컬 에셋)");
