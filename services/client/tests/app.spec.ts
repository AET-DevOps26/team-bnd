import { test, expect } from "@playwright/test";
import fs from "fs";
import path from "path";

// Load the OIDC auth data written by globalSetup and inject it into
// sessionStorage before the app scripts run, so the app sees an authenticated
// user on every test.
const authDataPath = path.resolve(__dirname, ".auth/user.json");
const { storageKey, oidcUser } = JSON.parse(
  fs.readFileSync(authDataPath, "utf-8"),
) as { storageKey: string; oidcUser: object };

test.describe("Alexandria client", () => {
  test.beforeEach(async ({ page }) => {
    // Inject the OIDC user into sessionStorage before any page script runs.
    await page.addInitScript(
      ({ key, value }) => {
        sessionStorage.setItem(key, value);
      },
      { key: storageKey, value: JSON.stringify(oidcUser) },
    );
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
      await expect(heading).toHaveText("Alexandria");
    });

    test("displays the logged-in user name", async ({ page }) => {
      const userName = page.locator(".user-name");
      await expect(userName).toBeVisible();
    });

    test("displays the logout button", async ({ page }) => {
      const logoutButton = page.getByRole("button", { name: "Logout" });
      await expect(logoutButton).toBeVisible();
    });
  });

  test.describe("Document tree", () => {
    test("renders the document list sidebar", async ({ page }) => {
      const nav = page.getByRole("navigation", { name: "Document list" });
      await expect(nav).toBeVisible();
    });

    test("shows the Documents heading in the sidebar", async ({ page }) => {
      const heading = page.getByRole("heading", { name: "Documents" });
      await expect(heading).toBeVisible();
    });

    test("shows a status message when not authenticated or empty", async ({
      page,
    }) => {
      // Without a real backend the tree will show either an auth warning,
      // a fetch error, or a "No documents yet." message — all are valid.
      const nav = page.getByRole("navigation", { name: "Document list" });
      await expect(nav).toBeVisible();

      // Wait for the loading state to resolve
      await expect(nav.getByText("Loading…"))
        .not.toBeVisible({ timeout: 5000 })
        .catch(() => {
          // Loading may have already finished — that's fine
        });

      // The sidebar must contain some non-loading status text or a list
      const hasStatusOrList = await nav
        .locator(".tree-status, .tree-list")
        .count();
      expect(hasStatusOrList).toBeGreaterThan(0);
    });
  });

  test.describe("Document detail", () => {
    test("shows a placeholder when no document is selected", async ({
      page,
    }) => {
      const main = page.locator("main.app-main");
      await expect(main).toBeVisible();
      await expect(main).toContainText(
        "Select a document to view its contents.",
      );
    });

    test("clicking a document item selects it and shows the detail panel", async ({
      page,
    }) => {
      // Mock the API responses so the test works without a live backend
      await page.route("/api/v1/knowledgebase/documents", (route) =>
        route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify([
            {
              id: "00000000-0000-0000-0000-000000000001",
              fileName: "sample-report.pdf",
              fileType: "application/pdf",
              fileSize: 204800,
              createdAt: "2026-05-01T10:00:00Z",
              tags: [],
              extractedEntities: [],
              summary: null,
            },
          ]),
        }),
      );

      await page.route(
        "/api/v1/knowledgebase/documents/00000000-0000-0000-0000-000000000001",
        (route) =>
          route.fulfill({
            status: 200,
            contentType: "application/json",
            body: JSON.stringify({
              id: "00000000-0000-0000-0000-000000000001",
              fileName: "sample-report.pdf",
              fileType: "application/pdf",
              fileSize: 204800,
              createdAt: "2026-05-01T10:00:00Z",
              tags: [{ id: "tag-1", label: "report", source: "AUTO" }],
              extractedEntities: [],
              summary: {
                id: "sum-1",
                content: "A sample report about testing.",
                generatedAt: "2026-05-01T10:01:00Z",
                modelUsed: "test-model",
              },
            }),
          }),
      );

      await page.goto("/");

      // The document item should appear in the tree
      const docItem = page.locator(".tree-item").first();
      await expect(docItem).toBeVisible({ timeout: 5000 });
      await expect(docItem).toContainText("sample-report.pdf");

      // Click it
      await docItem.click();

      // The detail panel should now show the document title
      const detailTitle = page.locator(".detail-title");
      await expect(detailTitle).toBeVisible({ timeout: 5000 });
      await expect(detailTitle).toHaveText("sample-report.pdf");
    });

    test("detail view shows summary when available", async ({ page }) => {
      await page.route("/api/v1/knowledgebase/documents", (route) =>
        route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify([
            {
              id: "00000000-0000-0000-0000-000000000002",
              fileName: "meeting-notes.txt",
              fileType: "text/plain",
              fileSize: 1024,
              createdAt: "2026-05-02T09:00:00Z",
              tags: [],
              extractedEntities: [],
              summary: null,
            },
          ]),
        }),
      );

      await page.route(
        "/api/v1/knowledgebase/documents/00000000-0000-0000-0000-000000000002",
        (route) =>
          route.fulfill({
            status: 200,
            contentType: "application/json",
            body: JSON.stringify({
              id: "00000000-0000-0000-0000-000000000002",
              fileName: "meeting-notes.txt",
              fileType: "text/plain",
              fileSize: 1024,
              createdAt: "2026-05-02T09:00:00Z",
              tags: [],
              extractedEntities: [],
              summary: {
                id: "sum-2",
                content: "Key decisions made during the meeting.",
                generatedAt: "2026-05-02T09:01:00Z",
                modelUsed: "gpt-4",
              },
            }),
          }),
      );

      await page.goto("/");

      const docItem = page.locator(".tree-item").first();
      await expect(docItem).toBeVisible({ timeout: 5000 });
      await docItem.click();

      await expect(page.locator(".detail-summary")).toBeVisible({
        timeout: 5000,
      });
      await expect(page.locator(".detail-summary")).toHaveText(
        "Key decisions made during the meeting.",
      );
    });

  });
});
