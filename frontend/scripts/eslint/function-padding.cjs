module.exports = {
  meta: {
    type: "layout",
    fixable: "whitespace",
    schema: [],
    messages: { padding: "함수·메서드 앞뒤에 빈 줄을 한 줄 넣으세요." },
  },

  create(context) {
    const source = context.sourceCode;
    const checked = new Set();

    function checkGap(previous, next) {
      const comments = source.getCommentsBefore(next);

      const start = comments.find((comment) => {
        return comment.loc.start.line > previous.loc.end.line;
      }) ?? next;

      const end = source.getTokenBefore(start, { includeComments: true });

      if (start.loc.start.line - end.loc.end.line >= 2 || checked.has(start.range[0])) {
        return;
      }

      checked.add(start.range[0]);
      context.report({
        node: next,
        messageId: "padding",

        fix(fixer) {
          return fixer.insertTextBefore(start, "\n\n");
        },
      });
    }

    function checkFunction(node) {
      let item = node;

      while (item.parent) {
        const parent = item.parent;
        const siblings = parent.type === "ObjectExpression" ? parent.properties
          : parent.type === "Program" || parent.type === "BlockStatement" || parent.type === "ClassBody" ? parent.body
            : parent.type === "SwitchCase" ? parent.consequent : undefined;

        if (siblings) {
          const index = siblings.indexOf(item);

          if (index > 0) {
            checkGap(siblings[index - 1], item);
          }

          if (index >= 0 && index < siblings.length - 1) {
            checkGap(item, siblings[index + 1]);
          }

          return;
        }

        item = parent;
      }
    }

    return {
      FunctionDeclaration: checkFunction,
      FunctionExpression: checkFunction,
      ArrowFunctionExpression: checkFunction,
    };
  },
};
