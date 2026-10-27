const { defineConfig } = require("eslint/config");
const expoConfig = require("eslint-config-expo/flat");

module.exports = defineConfig([
  expoConfig,
  {
    files: ["**/*.cjs"],
    languageOptions: {
      sourceType: "commonjs",
      globals: { __dirname: "readonly" },
    },
  },
  { ignores: ["dist/**", "android/**", "ios/**"] },
]);
