import { test as base, expect } from "@playwright/test";
import fs from "fs";
import path from "path";

const nycOutput = path.resolve(__dirname, "../.nyc_output");

// Extends the Playwright `page` so that, after each test, any istanbul coverage
// the instrumented client exposed on window.__coverage__ is written to
// .nyc_output for `nyc report` to merge. Against a non-instrumented build there
// is no __coverage__, so this is a no-op.
export const test = base.extend({
  page: async ({ page }, use, testInfo) => {
    await use(page);
    try {
      const coverage = await page.evaluate(
        () => (window as unknown as { __coverage__?: unknown }).__coverage__,
      );
      if (coverage) {
        fs.mkdirSync(nycOutput, { recursive: true });
        fs.writeFileSync(
          path.join(nycOutput, `coverage-${testInfo.testId}.json`),
          JSON.stringify(coverage),
        );
      }
    } catch {
      // The page may already be closed, or the build isn't instrumented.
    }
  },
});

export { expect };
