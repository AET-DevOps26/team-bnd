import js from "@eslint/js";
import tseslint from "typescript-eslint";
import reactHooks from "eslint-plugin-react-hooks";
import globals from "globals";

export default tseslint.config(
  // schema.d.ts is generated from the OpenAPI spec; dist is build output.
  { ignores: ["dist/**", "src/api/schema.d.ts"] },

  // Application source: strict TypeScript rules plus the core hooks checks.
  {
    files: ["src/**/*.{ts,tsx}"],
    extends: [
      js.configs.recommended,
      tseslint.configs.strict,
      tseslint.configs.stylistic,
    ],
    plugins: { "react-hooks": reactHooks },
    languageOptions: { globals: globals.browser },
    rules: {
      "react-hooks/rules-of-hooks": "error",
      "react-hooks/exhaustive-deps": "warn",
    },
  },

  // Tooling config and Playwright specs run under Node, not in the browser.
  {
    files: ["tests/**/*.{ts,tsx}", "*.{ts,mts,cts,js,mjs,cjs}"],
    extends: [js.configs.recommended, tseslint.configs.recommended],
    languageOptions: { globals: globals.node },
  },
);
