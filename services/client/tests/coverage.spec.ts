import { test, expect } from "./fixtures";
import type { Page, Route } from "@playwright/test";
import fs from "fs";
import path from "path";

// Targeted flows that the main suite doesn't drive, added to lift e2e coverage
// past 90%. The main app.spec.ts covers rendering and API calls; this file
// exercises the interaction handlers (rename, delete, reprocess, previews,
// upload) and the signed-out login page.

const authDataPath = path.resolve(__dirname, ".auth/user.json");
const { storageKey, oidcUser } = JSON.parse(
  fs.readFileSync(authDataPath, "utf-8"),
) as { storageKey: string; oidcUser: object };

async function authenticate(page: Page) {
  await page.addInitScript(
    ({ key, value }) => sessionStorage.setItem(key, value),
    { key: storageKey, value: JSON.stringify(oidcUser) },
  );
}

function json(route: Route, data: unknown, status = 200) {
  return route.fulfill({
    status,
    contentType: "application/json",
    body: JSON.stringify(data),
  });
}

type Doc = Record<string, unknown> & { id: string };

interface RouteOpts {
  download?: { body: string; contentType: string };
  tags?: unknown[];
}

async function routeDocument(page: Page, doc: Doc, opts: RouteOpts = {}) {
  const base = "/api/v1/knowledgebase/documents";
  await page.route("/api/v1/knowledgebase/tags", (r) =>
    json(r, { tags: opts.tags ?? [] }),
  );
  await page.route(base, (r) =>
    r.request().method() === "GET" ? json(r, [doc]) : json(r, doc),
  );
  await page.route(`${base}/${doc.id}/download`, (r) =>
    r.fulfill({
      status: 200,
      contentType: opts.download?.contentType ?? "text/plain",
      body: opts.download?.body ?? "",
    }),
  );
  await page.route(`${base}/${doc.id}/reprocess/*`, (r) => json(r, doc));
  await page.route(`${base}/${doc.id}/tags/*`, (r) => json(r, doc));
  await page.route(`${base}/${doc.id}/tags`, (r) => json(r, doc));
  await page.route(`${base}/${doc.id}`, (r) => json(r, doc));
}

test.describe("Signed-out login page", () => {
  test("shows the login page and archive illustration when signed out", async ({
    page,
  }) => {
    await page.goto("/");
    await expect(
      page.getByRole("button", { name: "Enter the library" }),
    ).toBeVisible({ timeout: 10000 });
    await expect(page.locator("svg.archive-scene")).toBeVisible();
  });
});

test.describe("Interaction coverage", () => {
  test.beforeEach(async ({ page }) => {
    await authenticate(page);
    // Auto-accept the confirm() dialogs used by delete actions.
    page.on("dialog", (dialog) => dialog.accept());
  });

  test("drives the document detail actions on a text document", async ({
    page,
  }) => {
    const doc: Doc = {
      id: "00000000-0000-0000-0000-0000000000a1",
      fileName: "notes.txt",
      fileType: "text/plain",
      fileSize: 2 * 1024 * 1024,
      createdAt: "2026-05-01T10:00:00Z",
      tags: [{ id: "t-user", label: "mine", source: "USER" }],
      extractedEntities: [
        { id: "e1", name: "Ada Lovelace", type: "PERSON" },
      ],
      entitiesStatus: "COMPLETED",
      tagsStatus: "COMPLETED",
      summary: {
        id: "s1",
        content: "A concise summary.",
        status: "COMPLETED",
        modelUsed: "test-model",
      },
    };
    await routeDocument(page, doc, {
      download: { body: "plain text body", contentType: "text/plain" },
    });

    await page.goto("/documents");
    await page.locator(".tree-item").first().click();
    await expect(page.locator(".detail-title")).toHaveText("notes.txt");

    // File size renders in MB
    await expect(page.locator(".detail-meta")).toContainText("2.0 MB");
    // Plain-text preview
    await expect(page.locator(".plaintext-body")).toContainText(
      "plain text body",
    );

    // Reprocess each section
    const regenerate = page.getByRole("button", { name: "Regenerate" });
    const count = await regenerate.count();
    for (let i = 0; i < count; i++) {
      await regenerate.nth(i).click();
    }

    // Add a tag through the inline input
    await page.getByRole("button", { name: "Add a tag" }).click();
    await page.getByLabel("New tag name").fill("fresh");
    await page.getByLabel("New tag name").press("Enter");

    // Remove the existing user tag
    await page.getByRole("button", { name: "Remove tag mine" }).click();

    // Clicking a tag label activates the sidebar filter
    await page.locator(".tag__label--clickable").first().click();

    // Rename the document
    await page.getByRole("button", { name: "Rename document" }).click();
    await page.getByLabel("Document name").fill("renamed.txt");
    await page.getByLabel("Document name").press("Enter");

    // Delete it (dialog auto-accepted), detail returns to the placeholder
    await page.getByRole("button", { name: "Delete document" }).click();
    await expect(page.locator(".app-main")).toContainText(
      "Select a document to view its contents.",
    );
  });

  test("renders a PDF preview", async ({ page }) => {
    const doc: Doc = {
      id: "00000000-0000-0000-0000-0000000000a2",
      fileName: "paper.pdf",
      fileType: "application/pdf",
      fileSize: 4096,
      createdAt: "2026-05-01T10:00:00Z",
      tags: [],
      extractedEntities: [],
      entitiesStatus: "COMPLETED",
      tagsStatus: "COMPLETED",
      summary: null,
    };
    await routeDocument(page, doc, {
      download: { body: "%PDF-1.0", contentType: "application/pdf" },
    });
    await page.goto("/documents");
    await page.locator(".tree-item").first().click();
    await expect(page.locator("iframe.pdf-embed")).toBeVisible({
      timeout: 5000,
    });
  });

  test("renders a Markdown preview", async ({ page }) => {
    const doc: Doc = {
      id: "00000000-0000-0000-0000-0000000000a3",
      fileName: "readme.md",
      fileType: "text/markdown",
      fileSize: 512,
      createdAt: "2026-05-01T10:00:00Z",
      tags: [],
      extractedEntities: [],
      entitiesStatus: "COMPLETED",
      tagsStatus: "COMPLETED",
      summary: null,
    };
    await routeDocument(page, doc, {
      download: { body: "# Big Heading", contentType: "text/markdown" },
    });
    await page.goto("/documents");
    await page.locator(".tree-item").first().click();
    await expect(
      page.locator(".markdown-body").getByRole("heading", {
        name: "Big Heading",
      }),
    ).toBeVisible({ timeout: 5000 });
  });

  test("shows processing loaders and failure states", async ({ page }) => {
    const doc: Doc = {
      id: "00000000-0000-0000-0000-0000000000a4",
      fileName: "processing.txt",
      fileType: "text/plain",
      fileSize: 1024,
      createdAt: "2026-05-01T10:00:00Z",
      tags: [],
      extractedEntities: [],
      entitiesStatus: "FAILED",
      tagsStatus: "FAILED",
      summary: { id: "s", status: "PENDING" },
    };
    await routeDocument(page, doc, {
      download: { body: "body", contentType: "text/plain" },
    });
    await page.goto("/documents");
    // The tree item shows a loader while the summary is pending
    await expect(page.locator(".tree-item .dots-loader")).toBeVisible({
      timeout: 5000,
    });
    await page.locator(".tree-item").first().click();
    await expect(page.getByText("Tag generation failed.")).toBeVisible();
    await expect(page.getByText("Entity extraction failed.")).toBeVisible();
  });

  test("filters the document list by entity", async ({ page }) => {
    await page.route("/api/v1/knowledgebase/tags", (r) =>
      json(r, { tags: [] }),
    );
    await page.route("/api/v1/knowledgebase/documents", (r) =>
      json(r, [
        {
          id: "d-ada",
          fileName: "has-ada.txt",
          fileType: "text/plain",
          createdAt: "2026-05-01T10:00:00Z",
          tags: [],
          extractedEntities: [{ id: "e", name: "Ada", type: "PERSON" }],
        },
        {
          id: "d-none",
          fileName: "no-ada.txt",
          fileType: "text/plain",
          createdAt: "2026-05-01T10:00:00Z",
          tags: [],
          extractedEntities: [],
        },
      ]),
    );
    await page.goto("/documents");
    await expect(page.getByText("no-ada.txt")).toBeVisible({ timeout: 5000 });
    await page.getByRole("button", { name: /Filter by entity/ }).click();
    await page.getByRole("button", { name: /Ada/ }).click();
    await expect(page.getByText("no-ada.txt")).toBeHidden();
    await page.getByRole("button", { name: "Clear" }).click();
    await expect(page.getByText("no-ada.txt")).toBeVisible();
  });

  test("uploads a document from the file picker", async ({ page }) => {
    await page.route("/api/v1/knowledgebase/tags", (r) =>
      json(r, { tags: [] }),
    );
    await page.route("/api/v1/knowledgebase/documents", (r) => json(r, []));
    await page.route("/api/v1/knowledgebase/documents/upload", (r) =>
      json(r, { id: "up-1", fileName: "upload.pdf" }),
    );
    await page.goto("/documents");
    await page.locator(".upload-input").setInputFiles({
      name: "upload.pdf",
      mimeType: "application/pdf",
      buffer: Buffer.from("%PDF-1.0"),
    });
    await expect(
      page.locator(".upload-status--success"),
    ).toContainText("Document uploaded successfully.", { timeout: 5000 });
  });

  test("shows an error when the upload is rejected", async ({ page }) => {
    await page.route("/api/v1/knowledgebase/tags", (r) => json(r, { tags: [] }));
    await page.route("/api/v1/knowledgebase/documents", (r) => json(r, []));
    await page.route("/api/v1/knowledgebase/documents/upload", (r) =>
      r.fulfill({ status: 500, contentType: "application/json", body: "{}" }),
    );
    await page.goto("/documents");
    await page.locator(".upload-input").setInputFiles({
      name: "x.pdf",
      mimeType: "application/pdf",
      buffer: Buffer.from("%PDF-1.0"),
    });
    await expect(page.locator(".upload-status--error")).toBeVisible({
      timeout: 5000,
    });
  });

  test("highlights the drop zone and opens the picker via keyboard", async ({
    page,
  }) => {
    await page.route("/api/v1/knowledgebase/tags", (r) => json(r, { tags: [] }));
    await page.route("/api/v1/knowledgebase/documents", (r) => json(r, []));
    await page.goto("/documents");

    const area = page.locator(".upload-area");
    await area.dispatchEvent("dragover");
    await expect(area).toHaveClass(/upload-area--drag-over/);
    await area.dispatchEvent("dragleave");
    await expect(area).not.toHaveClass(/upload-area--drag-over/);

    const chooserPromise = page.waitForEvent("filechooser");
    await area.focus();
    await page.keyboard.press("Enter");
    expect(await chooserPromise).toBeTruthy();
  });

  test("lists recent searches, re-runs one, and clears them", async ({
    page,
  }) => {
    await page.route("/api/v1/knowledgebase/tags", (r) => json(r, { tags: [] }));
    await page.route("/api/v1/knowledgebase/documents", (r) => json(r, []));
    await page.route(/\/knowledgebase\/history\/search/, (r) =>
      json(r, [
        {
          id: "h1",
          queryText: "prior query",
          resultCount: 2,
          timestamp: "2026-05-01T10:00:00Z",
        },
      ]),
    );
    await page.route(/\/knowledgebase\/search\/semantic/, (r) =>
      json(r, { results: [], fallbackUsed: false }),
    );
    await page.goto("/search");

    const recent = page.getByRole("button", { name: "prior query" });
    await expect(recent).toBeVisible({ timeout: 5000 });
    await recent.click();
    await expect(page.getByRole("searchbox")).toHaveValue("prior query");
    await page.getByRole("button", { name: "Clear" }).click();
  });

  test("deletes a document from the sidebar", async ({ page }) => {
    const doc: Doc = {
      id: "00000000-0000-0000-0000-0000000000b1",
      fileName: "delete-me.txt",
      fileType: "text/plain",
      fileSize: 512,
      createdAt: "2026-05-01T10:00:00Z",
      tags: [],
      extractedEntities: [],
    };
    await routeDocument(page, doc, {
      download: { body: "x", contentType: "text/plain" },
    });
    await page.goto("/documents");
    await page
      .getByRole("button", { name: "Delete delete-me.txt" })
      .click();
    await expect(
      page.getByRole("navigation", { name: "Document list" }),
    ).toBeVisible();
  });
});
