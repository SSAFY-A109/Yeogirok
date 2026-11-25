const assert = require("node:assert/strict");
const { Linter } = require("eslint");
const configuration = require("../eslint.config");

const conventions = configuration.find((entry) => {
  return entry.name === "yeogirok/conventions";
});

const rules = Object.fromEntries(Object.entries(conventions.rules).filter(([name]) => {
  return !name.startsWith("react");
}));

const config = [{ plugins: conventions.plugins, rules }];
const linter = new Linter();
const cases = [
  {
    input: "prepare(); if (ready) run(); else stop(); finish();",
    expected: "prepare();\n\nif (ready) {\n  run();\n} else {\n  stop();\n}\n\nfinish();\n",
  },
  {
    input: "prepare(); for (const item of items) run(item); finish();",
    expected: "prepare();\n\nfor (const item of items) {\n  run(item);\n}\n\nfinish();\n",
  },
  {
    input: "prepare(); for (let i = 0; i < 2; i++) run(i); finish();",
    expected: "prepare();\n\nfor (let i = 0; i < 2; i++) {\n  run(i);\n}\n\nfinish();\n",
  },
  {
    input: "prepare(); if (first) run(); else if (second) wait(); else stop(); finish();",
    expected: "prepare();\n\nif (first) {\n  run();\n} else if (second) {\n  wait();\n} else {\n  stop();\n}\n\nfinish();\n",
  },
  {
    input: "prepare(); try { run(); } catch (error) { stop(error); } finally { clean(); } finish();",
    expected: "prepare();\n\ntry {\n  run();\n} catch (error) {\n  stop(error);\n} finally {\n  clean();\n}\n\nfinish();\n",
  },
  {
    input: "const before = 1; const fn = () => before; const after = fn();",
    expected: "const before = 1;\n\nconst fn = () => {\n  return before;\n};\n\nconst after = fn();\n",
  },
  {
    input: "prepare(); function run() { work(); } finish();",
    expected: "prepare();\n\nfunction run() {\n  work();\n}\n\nfinish();\n",
  },
  {
    input: "prepare(); const run = function () { work(); }; finish();",
    expected: "prepare();\n\nconst run = function () {\n  work();\n};\n\nfinish();\n",
  },
  {
    input: "const service = { value: 1, run() { work(); }, stop: () => done(), ready: true };",
    expected: "const service = { value: 1,\n\n  run() {\n    work();\n  },\n\n  stop: () => {\n    return done();\n  },\n\n  ready: true };\n",
  },
  {
    input: "class Service { value = 1; run() { work(); } ready = true; }",
    expected: "class Service {\n  value = 1;\n\n  run() {\n    work();\n  }\n\n  ready = true;\n}\n",
  },
  {
    input: "prepare(); items.map((item) => item.id); finish();",
    expected: "prepare();\n\nitems.map((item) => {\n  return item.id;\n});\n\nfinish();\n",
  },
  {
    input: "prepare();\n/** Handles the action. */\nconst run = () => work();\nfinish();",
    expected: "prepare();\n\n/** Handles the action. */\nconst run = () => {\n  return work();\n};\n\nfinish();\n",
  },
  {
    input: "\n\nfunction run() {\n\n  prepare();\n\n\n  if (ready) { work(); }\n  finish();\n\n}\n\n",
    expected: "function run() {\n  prepare();\n\n  if (ready) {\n    work();\n  }\n\n  finish();\n}\n",
  },
];

for (const { input, expected } of cases) {
  const result = linter.verifyAndFix(input, config);
  assert.deepEqual(result.messages, [], input);
  assert.equal(result.output, expected, input);
  assert.equal(linter.verifyAndFix(result.output, config).fixed, false, input);
}

console.log(`ESLint conventions: ${cases.length} autofix and repeat checks passed`);
