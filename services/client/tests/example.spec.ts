import { test, expect } from "@playwright/test";

test.describe("Alexandria client", () => {
  test.beforeEach(async ({ page }) => {
    await page.goto("/");
  });

  test.describe("Page metadata", () => {
    test("has correct document title", async ({ page }) => {
      await expect(page).toHaveTitle("Alexandria");
    });
  });

  test.describe("Landing page content", () => {
    test("displays the main heading", async ({ page }) => {
      const heading = page.getByRole("heading", { level: 1 });
      await expect(heading).toBeVisible();
      await expect(heading).toHaveText("Alexandria — Document Summarization");
    });

    test("displays the product description", async ({ page }) => {
      const description = page.getByText(
        "Alexandria helps users upload documents and get concise summaries",
      );
      await expect(description).toBeVisible();
    });

    test("description mentions key features", async ({ page }) => {
      const body = page.locator("body");
      await expect(body).toContainText("summaries");
      await expect(body).toContainText("extracted tags");
      await expect(body).toContainText("searchable knowledge");
    });
  });
});
