const assert = require("node:assert/strict");
const { readFileSync } = require("node:fs");
const ts = require("typescript");

require.extensions[".ts"] = (module, filename) => {
  const source = readFileSync(filename, "utf8");
  const { outputText } = ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
  });
  module._compile(outputText, filename);
};

const { joinChatPages, mergeChatMessages, normalizeChatMessages } = require("../features/room/chat-model.ts");

const history = Object.freeze(Array.from({ length: 10_000 }, (_, index) => {
  return { id: (index + 1) * 2, body: "이력" };
}));

const newest = { id: 20_002, body: "신규" };
const late = { id: 101, body: "지연" };
const updated = { id: 102, body: "갱신" };
const older = { id: 1, body: "이전" };

assert.deepEqual(mergeChatMessages(history, [newest]), [...history, newest]);
assert.deepEqual(mergeChatMessages(history, [late]), [...history.slice(0, 50), late, ...history.slice(50)]);
assert.deepEqual(mergeChatMessages(history, [updated]), [...history.slice(0, 50), updated, ...history.slice(51)]);
assert.deepEqual(mergeChatMessages(history, [older]), [older, ...history]);
assert.deepEqual(mergeChatMessages([], [newest]), [newest]);
assert.deepEqual(mergeChatMessages(history, []), history);

const recovered = normalizeChatMessages([newest, history[50], late, older, updated]);
assert.deepEqual(recovered, [older, late, updated, newest]);
assert.deepEqual(mergeChatMessages(history, recovered), [older, ...history.slice(0, 50), late, updated, ...history.slice(51), newest]);

const olderDuplicate = { id: 102, body: "이전 페이지 값" };
const pages = [{ messages: history.slice(50) }, { messages: [...history.slice(0, 50), olderDuplicate, older] }];
assert.deepEqual(joinChatPages(pages), [older, ...history]);

const mergedUpdate = mergeChatMessages(joinChatPages(pages), recovered).find(({ id }) => {
  return id === updated.id;
});

assert.equal(mergedUpdate, updated);

console.log("Chat merge checks passed");
