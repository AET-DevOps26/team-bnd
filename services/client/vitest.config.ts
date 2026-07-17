import { defineConfig } from "vitest/config";

// Unit tests run in jsdom so component tests can render real DOM. The Playwright
// e2e suite under tests/ is deliberately excluded (see `include`), so the two
// test runners never pick up each other's files.
export default defineConfig({
  test: {
    environment: "jsdom",
    setupFiles: ["./src/test/setup.ts"],
    include: ["src/**/*.test.{ts,tsx}"],
    // Give the api client an absolute base URL so openapi-fetch can build a
    // valid Request under node (relative URLs only resolve in a real browser).
    env: { VITE_API_URL: "http://localhost" },
    coverage: {
      provider: "v8",
      reporter: ["text", "cobertura", "html"],
      reportsDirectory: "./coverage",
      // Report on the whole app, not just files a test happens to import, so the
      // badge reflects real coverage. Generated and entrypoint files are excluded.
      include: ["src/**/*.{ts,tsx}"],
      exclude: [
        "src/**/*.test.{ts,tsx}",
        "src/test/**",
        "src/**/__mocks__/**",
        "src/api/schema.d.ts",
        "src/vite-env.d.ts",
        "src/main.tsx",
      ],
    },
  },
});
