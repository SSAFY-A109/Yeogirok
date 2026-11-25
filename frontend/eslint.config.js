const { defineConfig } = require("eslint/config");
const expoConfig = require("eslint-config-expo/flat");
const functionPadding = require("./scripts/eslint/function-padding.cjs");

module.exports = defineConfig([
  expoConfig,
  {
    name: "yeogirok/conventions",
    plugins: { local: { rules: { "function-padding": functionPadding } } },
    rules: {
      curly: ["error", "all"],
      "brace-style": ["error", "1tbs", { allowSingleLine: false }],
      "arrow-body-style": ["error", "always"],
      "padding-line-between-statements": [
        "error",
        { blankLine: "always", prev: "*", next: "block-like" },
        { blankLine: "always", prev: "block-like", next: "*" },
      ],
      "local/function-padding": "error",
      "padded-blocks": ["error", "never"],
      "no-multiple-empty-lines": ["error", { max: 1, maxBOF: 0, maxEOF: 0 }],
      indent: ["error", 2, { SwitchCase: 1 }],
      "keyword-spacing": "error",
      "space-before-blocks": "error",
      "space-infix-ops": "error",
      "arrow-spacing": "error",
      "comma-spacing": "error",
      "key-spacing": "error",
      "object-curly-spacing": ["error", "always"],
      semi: ["error", "always"],
      quotes: ["error", "double", { avoidEscape: true }],
      "no-trailing-spaces": "error",
      "eol-last": ["error", "always"],
      eqeqeq: ["error", "always", { null: "ignore" }],
      "prefer-const": "error",
      "react-hooks/rules-of-hooks": "error",
      "react-hooks/exhaustive-deps": "error",
      "react/jsx-key": "error",
      "react/jsx-no-duplicate-props": "error",
      "react/jsx-curly-brace-presence": ["error", { props: "never", children: "never" }],
      "react/self-closing-comp": "error",
    },
  },
  {
    files: ["**/*.cjs"],
    languageOptions: {
      sourceType: "commonjs",
      globals: { __dirname: "readonly" },
    },
  },
  {
    ignores: [
      "dist/**", "android/**", "ios/**", ".expo/**", "public/mockServiceWorker.js",
      "lib/theme.ts", "tailwind.config.js",
    ],
  },
]);
