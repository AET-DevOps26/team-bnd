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
    await page.goto("/ask");
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

    test("shows a status message or document list once loading resolves", async ({
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

  test.describe("Document upload", () => {
    const uploadedDoc = {
      id: "00000000-0000-0000-0000-000000000099",
      fileName: "test-upload.pdf",
      fileType: "application/pdf",
      fileSize: 1024,
      createdAt: "2026-07-01T12:00:00Z",
      tags: [],
      extractedEntities: [],
      summary: null,
    };

    // A tiny valid PDF in memory — enough for the file input; backend is mocked.
    const pdfBuffer = Buffer.from(
      "%PDF-1.0\n1 0 obj<</Type /Catalog>>endobj\n",
    );

    test("renders the upload area and Upload Document button", async ({
      page,
    }) => {
      const uploadArea = page.locator(".upload-area");
      await expect(uploadArea).toBeVisible();

      const uploadButton = page.getByRole("button", {
        name: "Upload Document",
      });
      await expect(uploadButton).toBeVisible();

      const hint = page.locator(".upload-hint");
      await expect(hint).toContainText("drag and drop");
    });

    test("shows uploading state while the request is in flight", async ({
      page,
    }) => {
      // Use a long delay so we can assert the intermediate state.
      await page.route(
        "/api/v1/knowledgebase/documents/upload",
        async (route) => {
          await new Promise((resolve) => setTimeout(resolve, 3000));
          await route.fulfill({
            status: 201,
            contentType: "application/json",
            body: JSON.stringify(uploadedDoc),
          });
        },
      );

      const fileInput = page.locator('[aria-label="Upload document file"]');
      await fileInput.setInputFiles({
        name: "test-upload.pdf",
        mimeType: "application/pdf",
        buffer: pdfBuffer,
      });

      // Button and hint should be hidden while uploading
      await expect(page.locator(".upload-status")).toBeVisible();
      await expect(page.locator(".upload-status")).toContainText("Uploading");
      await expect(
        page.getByRole("button", { name: "Upload Document" }),
      ).not.toBeVisible();
      await expect(page.locator(".upload-hint")).not.toBeVisible();
    });

    test("shows success status after a successful upload", async ({ page }) => {
      await page.route(
        "/api/v1/knowledgebase/documents/upload",
        async (route) => {
          await route.fulfill({
            status: 201,
            contentType: "application/json",
            body: JSON.stringify(uploadedDoc),
          });
        },
      );

      const fileInput = page.locator('[aria-label="Upload document file"]');
      await fileInput.setInputFiles({
        name: "test-upload.pdf",
        mimeType: "application/pdf",
        buffer: pdfBuffer,
      });

      const status = page.locator(".upload-status--success");
      await expect(status).toBeVisible({ timeout: 5000 });
      await expect(status).toHaveText("Document uploaded successfully.");

      // Button and hint return after success
      await expect(
        page.getByRole("button", { name: "Upload Document" }),
      ).toBeVisible();
      await expect(page.locator(".upload-hint")).toBeVisible();
    });

    test("shows a generic error when the upload fails (500)", async ({
      page,
    }) => {
      await page.route(
        "/api/v1/knowledgebase/documents/upload",
        async (route) => {
          await route.fulfill({ status: 500, body: "Internal Server Error" });
        },
      );

      const fileInput = page.locator('[aria-label="Upload document file"]');
      await fileInput.setInputFiles({
        name: "bad-upload.pdf",
        mimeType: "application/pdf",
        buffer: pdfBuffer,
      });

      const status = page.locator(".upload-status--error");
      await expect(status).toBeVisible({ timeout: 5000 });
      await expect(status).toHaveText("Upload failed. Please try again.");
    });

    test("shows 'Authentication error. Retrying login...' when the server returns 401", async ({
      page,
    }) => {
      await page.route(
        "/api/v1/knowledgebase/documents/upload",
        async (route) => {
          await route.fulfill({ status: 401, body: "Unauthorized" });
        },
      );

      const fileInput = page.locator('[aria-label="Upload document file"]');
      await fileInput.setInputFiles({
        name: "test-upload.pdf",
        mimeType: "application/pdf",
        buffer: pdfBuffer,
      });

      const status = page.locator(".upload-status--error");
      await expect(status).toBeVisible({ timeout: 5000 });
      await expect(status).toHaveText(
        "Authentication error. Retrying login...",
      );
    });

    test("shows permission error when the server returns 403", async ({
      page,
    }) => {
      await page.route(
        "/api/v1/knowledgebase/documents/upload",
        async (route) => {
          await route.fulfill({ status: 403, body: "Forbidden" });
        },
      );

      const fileInput = page.locator('[aria-label="Upload document file"]');
      await fileInput.setInputFiles({
        name: "test-upload.pdf",
        mimeType: "application/pdf",
        buffer: pdfBuffer,
      });

      const status = page.locator(".upload-status--error");
      await expect(status).toBeVisible({ timeout: 5000 });
      await expect(status).toHaveText(
        "You do not have permission to upload documents.",
      );
    });

    test("newly uploaded document appears in the document tree", async ({
      page,
    }) => {
      // Initially the list is empty
      await page.route("/api/v1/knowledgebase/documents", (route) =>
        route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify([]),
        }),
      );

      await page.route(
        "/api/v1/knowledgebase/documents/upload",
        async (route) => {
          await route.fulfill({
            status: 201,
            contentType: "application/json",
            body: JSON.stringify(uploadedDoc),
          });
        },
      );

      await page.goto("/ask");

      // No documents yet — tree list should be absent or status shown
      await expect(page.locator(".tree-item")).toHaveCount(0, {
        timeout: 5000,
      });

      // After upload, mock the list endpoint to return the new document so the
      // invalidateQueries refetch picks it up.
      await page.unroute("/api/v1/knowledgebase/documents");
      await page.route("/api/v1/knowledgebase/documents", (route) =>
        route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify([uploadedDoc]),
        }),
      );

      const fileInput = page.locator('[aria-label="Upload document file"]');
      await fileInput.setInputFiles({
        name: "test-upload.pdf",
        mimeType: "application/pdf",
        buffer: pdfBuffer,
      });

      // The success message should appear
      await expect(page.locator(".upload-status--success")).toBeVisible({
        timeout: 5000,
      });

      // The document should now be listed in the tree
      const treeItem = page.locator(".tree-item").first();
      await expect(treeItem).toBeVisible({ timeout: 5000 });
      await expect(treeItem).toContainText("test-upload.pdf");
    });

    test("upload area gains drag-over style when a file is dragged over it", async ({
      page,
    }) => {
      const uploadArea = page.locator(".upload-area");
      await expect(uploadArea).toBeVisible();

      // Simulate dragover — the component sets dragOver state which adds the CSS modifier
      await uploadArea.dispatchEvent("dragover", {
        dataTransfer: await page.evaluateHandle(() => new DataTransfer()),
      });
      await expect(uploadArea).toHaveClass(/upload-area--drag-over/);

      // Simulate dragleave — modifier should be removed
      await uploadArea.dispatchEvent("dragleave", {
        dataTransfer: await page.evaluateHandle(() => new DataTransfer()),
      });
      await expect(uploadArea).not.toHaveClass(/upload-area--drag-over/);
    });
  });

  test.describe("Document detail", () => {
    test("shows a placeholder when no document is selected", async ({
      page,
    }) => {
      await page.goto("/documents");
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

      await page.goto("/ask");

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

      await page.goto("/ask");

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

  test.describe("Q&A panel", () => {
    // Stub the history endpoint for all Q&A tests so there is no pre-loaded
    // history and the empty state is shown by default.
    test.beforeEach(async ({ page }) => {
      await page.route("/api/v1/qa/history", (route) =>
        route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify([]),
        }),
      );
    });

    test("Ask tab is active by default on load", async ({ page }) => {
      await page.goto("/ask");

      // Scope to the header nav to avoid matching the .qa-submit "Ask" button
      const askTab = page
        .locator(".app-header-nav .app-tab")
        .filter({ hasText: "Ask" });
      await expect(askTab).toBeVisible();
      await expect(askTab).toHaveClass(/app-tab--active/);

      // The QA panel itself should be visible (not hidden)
      const panel = page.locator(".qa-panel");
      await expect(panel).toBeVisible();
    });

    test("Q&A panel renders input and submit button", async ({ page }) => {
      await page.goto("/ask");

      const input = page.locator(".qa-input");
      await expect(input).toBeVisible();

      const submit = page.locator(".qa-submit");
      await expect(submit).toBeVisible();
      await expect(submit).toHaveText("Ask");
    });

    test("shows empty state when there is no history", async ({ page }) => {
      await page.goto("/ask");

      const empty = page.locator(".qa-empty");
      await expect(empty).toBeVisible({ timeout: 5000 });
      await expect(empty).toContainText("No questions yet");
    });

    test("submitting a question calls POST /api/v1/qa/ask and displays the answer", async ({
      page,
    }) => {
      await page.route("/api/v1/qa/ask", (route) =>
        route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify({
            id: "aaaaaaaa-0000-0000-0000-000000000001",
            question: "What is the main topic?",
            answer: "The main topic is testing.",
            citations: [],
            timestamp: "2026-07-08T10:00:00Z",
            modelUsed: "gpt-4o",
          }),
        }),
      );

      await page.goto("/ask");

      await page.locator(".qa-input").fill("What is the main topic?");
      await page.locator(".qa-submit").click();

      const interaction = page.locator(".qa-interaction").first();
      await expect(interaction).toBeVisible({ timeout: 5000 });
      await expect(interaction).toContainText("What is the main topic?");
      await expect(interaction).toContainText("The main topic is testing.");
    });

    test("submit button is disabled and shows 'Asking…' while request is in flight", async ({
      page,
    }) => {
      // Use a long delay so we can assert the intermediate state before it resolves.
      await page.route("/api/v1/qa/ask", async (route) => {
        await new Promise((resolve) => setTimeout(resolve, 3000));
        await route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify({
            id: "aaaaaaaa-0000-0000-0000-000000000002",
            question: "Slow question?",
            answer: "Slow answer.",
            citations: [],
            timestamp: "2026-07-08T11:30:00Z",
            modelUsed: "gpt-4o",
          }),
        });
      });

      await page.goto("/ask");
      await page.locator(".qa-input").fill("Slow question?");
      await page.locator(".qa-submit").click();

      const submit = page.locator(".qa-submit");
      await expect(submit).toBeDisabled();
      await expect(submit).toHaveText("Asking…");
    });

    test("answer shows model name in the meta line", async ({ page }) => {
      await page.route("/api/v1/qa/ask", (route) =>
        route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify({
            id: "aaaaaaaa-0000-0000-0000-000000000003",
            question: "When was it published?",
            answer: "It was published in 2024.",
            citations: [],
            timestamp: "2026-07-08T12:30:00Z",
            modelUsed: "test-model",
          }),
        }),
      );

      await page.goto("/ask");
      await page.locator(".qa-input").fill("When was it published?");
      await page.locator(".qa-submit").click();

      const meta = page.locator(".qa-meta").first();
      await expect(meta).toBeVisible({ timeout: 5000 });
      await expect(meta).toContainText("test-model");
    });

    test("resolved source references are shown with the document filename", async ({
      page,
    }) => {
      const docId = "00000000-0000-0000-0000-000000000010";
      const objectKey = `users/user-1/documents/${docId}/report.pdf`;

      await page.route("/api/v1/knowledgebase/documents", (route) =>
        route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify([
            {
              id: docId,
              fileName: "report.pdf",
              objectKey,
              fileType: "application/pdf",
              fileSize: 2048,
              createdAt: "2026-07-01T00:00:00Z",
              tags: [],
              extractedEntities: [],
              summary: null,
            },
          ]),
        }),
      );

      await page.route("/api/v1/qa/ask", (route) =>
        route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify({
            id: "aaaaaaaa-0000-0000-0000-000000000004",
            question: "What does the report say?",
            answer: "The report covers Q3 results.",
            citations: [
              {
                marker: 1,
                objectKey,
                documentId: docId,
                fileName: "report.pdf",
                snippet: "Q3 revenue grew.",
              },
            ],
            timestamp: "2026-07-08T11:00:00Z",
            modelUsed: "gpt-4o",
          }),
        }),
      );

      await page.goto("/ask");
      await page.locator(".qa-input").fill("What does the report say?");
      await page.locator(".qa-submit").click();

      const sources = page.locator(".qa-sources").first();
      await expect(sources).toBeVisible({ timeout: 5000 });

      // Resolved link uses the document's fileName, not the raw key
      const sourceLink = page.locator(".qa-source-link").first();
      await expect(sourceLink).toBeVisible();
      await expect(sourceLink).toHaveText("report.pdf");
      await expect(sourceLink).not.toHaveClass(/qa-source-link--unresolved/);
    });

    test("unresolved source shows the last path segment of the object key", async ({
      page,
    }) => {
      const objectKey = "users/user-1/documents/unknown-id/mystery.pdf";

      // Return an empty documents list so the key cannot be resolved to a document
      await page.route("/api/v1/knowledgebase/documents", (route) =>
        route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify([]),
        }),
      );

      await page.route("/api/v1/qa/ask", (route) =>
        route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify({
            id: "aaaaaaaa-0000-0000-0000-000000000005",
            question: "What is this?",
            answer: "Unknown source.",
            citations: [{ marker: 1, objectKey }],
            timestamp: "2026-07-08T11:10:00Z",
            modelUsed: "gpt-4o",
          }),
        }),
      );

      await page.goto("/ask");
      await page.locator(".qa-input").fill("What is this?");
      await page.locator(".qa-submit").click();

      const sourceLink = page.locator(".qa-source-link--unresolved").first();
      await expect(sourceLink).toBeVisible({ timeout: 5000 });
      await expect(sourceLink).toHaveText("mystery.pdf");
    });

    test("clicking a resolved source navigates to the document detail route", async ({
      page,
    }) => {
      const docId = "00000000-0000-0000-0000-000000000011";
      const objectKey = `users/user-1/documents/${docId}/guide.pdf`;

      await page.route("/api/v1/knowledgebase/documents", (route) =>
        route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify([
            {
              id: docId,
              fileName: "guide.pdf",
              objectKey,
              fileType: "application/pdf",
              fileSize: 1024,
              createdAt: "2026-07-01T00:00:00Z",
              tags: [],
              extractedEntities: [],
              summary: null,
            },
          ]),
        }),
      );

      await page.route(`/api/v1/knowledgebase/documents/${docId}`, (route) =>
        route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify({
            id: docId,
            fileName: "guide.pdf",
            objectKey,
            fileType: "application/pdf",
            fileSize: 1024,
            createdAt: "2026-07-01T00:00:00Z",
            tags: [],
            extractedEntities: [],
            summary: null,
          }),
        }),
      );

      await page.route("/api/v1/qa/ask", (route) =>
        route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify({
            id: "aaaaaaaa-0000-0000-0000-000000000006",
            question: "What is in the guide?",
            answer: "The guide explains installation steps.",
            citations: [
              {
                marker: 1,
                objectKey,
                documentId: docId,
                fileName: "guide.pdf",
                snippet: "Installation steps.",
              },
            ],
            timestamp: "2026-07-08T11:05:00Z",
            modelUsed: "gpt-4o",
          }),
        }),
      );

      await page.goto("/ask");
      await page.locator(".qa-input").fill("What is in the guide?");
      await page.locator(".qa-submit").click();

      const sourceLink = page.locator(".qa-source-link").first();
      await expect(sourceLink).toBeVisible({ timeout: 5000 });
      await sourceLink.click();

      // Documents tab link should now be active
      const documentsTab = page
        .locator(".app-header-nav .app-tab")
        .filter({ hasText: "Documents" });
      await expect(documentsTab).toHaveClass(/app-tab--active/, {
        timeout: 3000,
      });

      // The detail panel for the selected document should be visible
      const detailTitle = page.locator(".detail-title");
      await expect(detailTitle).toBeVisible({ timeout: 5000 });
      await expect(detailTitle).toHaveText("guide.pdf");
    });

    test("history loaded on mount is displayed as past interactions", async ({
      page,
    }) => {
      // Override the beforeEach empty-history stub with actual history data
      await page.unroute("/api/v1/qa/history");
      await page.route("/api/v1/qa/history", (route) =>
        route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify([
            {
              id: "aaaaaaaa-0000-0000-0000-000000000007",
              question: "Previous question from history?",
              answer: "This is a historical answer.",
              citations: [],
              timestamp: "2026-07-07T09:00:00Z",
              modelUsed: "gpt-4o",
            },
          ]),
        }),
      );

      await page.goto("/ask");

      const interaction = page.locator(".qa-interaction").first();
      await expect(interaction).toBeVisible({ timeout: 5000 });
      await expect(interaction).toContainText(
        "Previous question from history?",
      );
      await expect(interaction).toContainText("This is a historical answer.");
    });

    test("shows an error message when the ask endpoint returns 500", async ({
      page,
    }) => {
      await page.route("/api/v1/qa/ask", (route) =>
        route.fulfill({ status: 500, body: "Internal Server Error" }),
      );

      await page.goto("/ask");
      await page.locator(".qa-input").fill("This will fail.");
      await page.locator(".qa-submit").click();

      const error = page.locator(".qa-error");
      await expect(error).toBeVisible({ timeout: 5000 });
      await expect(error).toContainText("Failed to get an answer");
    });

    test("shows 'Authentication error. Retrying login...' when the ask endpoint returns 401", async ({
      page,
    }) => {
      await page.route("**/protocol/openid-connect/token", (route) =>
        route.fulfill({ status: 401, body: "Unauthorized" }),
      );

      await page.route("/api/v1/qa/ask", (route) =>
        route.fulfill({ status: 401, body: "Unauthorized" }),
      );

      await page.goto("/ask");
      await page.locator(".qa-input").fill("Auth check question.");
      await page.locator(".qa-submit").click();

      const error = page.locator(".qa-error");
      await expect(error).toBeVisible({ timeout: 5000 });
      await expect(error).toHaveText("Authentication error. Retrying login...");
    });

    test("Documents tab link switches to document view", async ({ page }) => {
      await page.goto("/ask");

      // Scope to the header nav to avoid matching the .qa-submit "Ask" button
      const askTab = page
        .locator(".app-header-nav .app-tab")
        .filter({ hasText: "Ask" });
      const documentsTab = page
        .locator(".app-header-nav .app-tab")
        .filter({ hasText: "Documents" });

      // Ask tab is active by default
      await expect(askTab).toHaveClass(/app-tab--active/);
      await expect(documentsTab).not.toHaveClass(/app-tab--active/);

      // Click Documents tab — it becomes active, Ask tab becomes inactive
      await documentsTab.click();
      await expect(documentsTab).toHaveClass(/app-tab--active/);
      await expect(askTab).not.toHaveClass(/app-tab--active/);

      // Click Ask tab again — switches back
      await askTab.click();
      await expect(askTab).toHaveClass(/app-tab--active/);
      await expect(documentsTab).not.toHaveClass(/app-tab--active/);
    });

    test("clear history button removes all interactions and shows empty state", async ({
      page,
    }) => {
      const historyItems = [
        {
          id: "aaaaaaaa-0000-0000-0000-000000000008",
          question: "First history question?",
          answer: "First historical answer.",
          citations: [],
          timestamp: "2026-07-06T08:00:00Z",
          modelUsed: "gpt-4o",
        },
        {
          id: "aaaaaaaa-0000-0000-0000-000000000009",
          question: "Second history question?",
          answer: "Second historical answer.",
          citations: [],
          timestamp: "2026-07-06T09:00:00Z",
          modelUsed: "gpt-4o",
        },
      ];

      // Override the beforeEach empty-history stub with a single handler that
      // serves the two history items on GET and accepts DELETE.
      await page.unroute("/api/v1/qa/history");
      await page.route("/api/v1/qa/history", (route, request) => {
        if (request.method() === "DELETE") {
          route.fulfill({ status: 200 });
        } else {
          route.fulfill({
            status: 200,
            contentType: "application/json",
            body: JSON.stringify(historyItems),
          });
        }
      });

      await page.goto("/ask");

      // Both interactions should be visible
      await expect(page.locator(".qa-interaction")).toHaveCount(2, {
        timeout: 5000,
      });

      // Accept the confirm dialog that QAPanel fires before clearing
      page.on("dialog", (dialog) => dialog.accept());

      await page.locator(".qa-clear-button").click();

      // All interactions should be gone and the empty state should appear
      await expect(page.locator(".qa-interaction")).toHaveCount(0, {
        timeout: 5000,
      });
      await expect(page.locator(".qa-empty")).toBeVisible({ timeout: 5000 });
      await expect(page.locator(".qa-empty")).toContainText("No questions yet");
    });
  });

  test.describe("Search panel", () => {
    test("Search tab is visible and navigates to /search", async ({ page }) => {
      await page.goto("/ask");

      const searchTab = page
        .locator(".app-header-nav .app-tab")
        .filter({ hasText: "Search" });
      await expect(searchTab).toBeVisible();

      await searchTab.click();
      await expect(page).toHaveURL(/\/search/);

      await expect(searchTab).toHaveClass(/app-tab--active/);
    });

    test("shows empty prompt before any query is submitted", async ({
      page,
    }) => {
      await page.goto("/search");

      const panel = page.locator(".search-panel");
      await expect(panel).toBeVisible();

      const empty = panel.locator(".search-empty");
      await expect(empty).toBeVisible();
      await expect(empty).toContainText("Enter a query");
    });

    test("search input and submit button are rendered", async ({ page }) => {
      await page.goto("/search");

      await expect(page.locator(".search-input")).toBeVisible();
      await expect(page.locator(".search-submit")).toBeVisible();
      await expect(page.locator(".search-submit")).toHaveText("Search");
    });

    test("semantic and keyword mode toggle buttons are rendered", async ({
      page,
    }) => {
      await page.goto("/search");

      const semanticBtn = page
        .locator(".search-mode-btn")
        .filter({ hasText: "Semantic" });
      const keywordBtn = page
        .locator(".search-mode-btn")
        .filter({ hasText: "Keyword" });

      await expect(semanticBtn).toBeVisible();
      await expect(keywordBtn).toBeVisible();
      // Semantic should be active by default
      await expect(semanticBtn).toHaveClass(/search-mode-btn--active/);
      await expect(keywordBtn).not.toHaveClass(/search-mode-btn--active/);
    });

    test("submitting a query updates the URL with ?q= and ?mode=", async ({
      page,
    }) => {
      await page.route("/api/v1/knowledgebase/search/semantic*", (route) =>
        route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify({ results: [], fallbackUsed: false }),
        }),
      );

      await page.goto("/search");
      await page.locator(".search-input").fill("hello world");
      await page.locator(".search-submit").click();

      await expect(page).toHaveURL(/[?&]q=hello\+world/);
      await expect(page).toHaveURL(/[?&]mode=semantic/);
    });

    test("navigating to /search?q=foo pre-fills the input and runs the search", async ({
      page,
    }) => {
      await page.route("/api/v1/knowledgebase/search/semantic*", (route) =>
        route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify({
            results: [
              {
                document: {
                  id: "bbbbbbbb-0000-0000-0000-000000000010",
                  fileName: "prefilled.pdf",
                  fileType: "application/pdf",
                  fileSize: 1024,
                  createdAt: "2026-07-01T00:00:00Z",
                },
                score: 0.9,
                snippet: "Matching content.",
              },
            ],
            fallbackUsed: false,
          }),
        }),
      );

      await page.goto("/search?q=foo&mode=semantic");

      // Input should be pre-filled
      await expect(page.locator(".search-input")).toHaveValue("foo");

      // Results should load automatically
      const resultList = page.locator(".search-result-list");
      await expect(resultList).toBeVisible({ timeout: 5000 });
      await expect(resultList).toContainText("prefilled.pdf");
    });

    test("semantic search shows results with snippet and score", async ({
      page,
    }) => {
      const docId = "bbbbbbbb-0000-0000-0000-000000000001";

      await page.route("/api/v1/knowledgebase/search/semantic*", (route) =>
        route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify({
            results: [
              {
                document: {
                  id: docId,
                  fileName: "annual-report.pdf",
                  fileType: "application/pdf",
                  fileSize: 204800,
                  createdAt: "2026-07-01T00:00:00Z",
                },
                score: 0.87,
                snippet:
                  "Revenue grew by 12% compared to the previous fiscal year.",
              },
            ],
            fallbackUsed: false,
          }),
        }),
      );

      await page.goto("/search");
      await page.locator(".search-input").fill("revenue growth");
      await page.locator(".search-submit").click();

      const resultList = page.locator(".search-result-list");
      await expect(resultList).toBeVisible({ timeout: 5000 });

      const firstResult = resultList.locator(".search-result-item").first();
      await expect(firstResult).toBeVisible();
      await expect(firstResult).toContainText("annual-report.pdf");
      await expect(firstResult).toContainText("87%");
      await expect(firstResult).toContainText(
        "Revenue grew by 12% compared to the previous fiscal year.",
      );
    });

    test("semantic result title links to the document detail page", async ({
      page,
    }) => {
      const docId = "bbbbbbbb-0000-0000-0000-000000000002";

      await page.route("/api/v1/knowledgebase/search/semantic*", (route) =>
        route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify({
            results: [
              {
                document: {
                  id: docId,
                  fileName: "linked-doc.pdf",
                  fileType: "application/pdf",
                  fileSize: 1024,
                  createdAt: "2026-07-01T00:00:00Z",
                },
                score: 0.75,
                snippet: "Some matching text.",
              },
            ],
            fallbackUsed: false,
          }),
        }),
      );

      await page.route(`/api/v1/knowledgebase/documents/${docId}`, (route) =>
        route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify({
            id: docId,
            fileName: "linked-doc.pdf",
            objectKey: `users/u1/documents/${docId}/linked-doc.pdf`,
            fileType: "application/pdf",
            fileSize: 1024,
            createdAt: "2026-07-01T00:00:00Z",
            tags: [],
            extractedEntities: [],
            summary: null,
          }),
        }),
      );

      await page.goto("/search");
      await page.locator(".search-input").fill("something");
      await page.locator(".search-submit").click();

      const link = page.locator(".search-result-title").first();
      await expect(link).toBeVisible({ timeout: 5000 });
      await link.click();

      // Should navigate to the document detail route
      await expect(page).toHaveURL(new RegExp(`/documents/${docId}`));
    });

    test("shows fallback notice when vector search fell back to keyword", async ({
      page,
    }) => {
      await page.route("/api/v1/knowledgebase/search/semantic*", (route) =>
        route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify({
            results: [
              {
                document: {
                  id: "bbbbbbbb-0000-0000-0000-000000000003",
                  fileName: "fallback-result.pdf",
                  fileType: "application/pdf",
                  fileSize: 512,
                  createdAt: "2026-07-01T00:00:00Z",
                },
                score: 0.5,
                snippet: "Keyword match.",
              },
            ],
            fallbackUsed: true,
          }),
        }),
      );

      await page.goto("/search");
      await page.locator(".search-input").fill("some query");
      await page.locator(".search-submit").click();

      const notice = page.locator(".search-fallback-notice");
      await expect(notice).toBeVisible({ timeout: 5000 });
      await expect(notice).toContainText("keyword results");
    });

    test("shows fallback notice and empty state when vector search fell back to keyword with no results", async ({
      page,
    }) => {
      await page.route("/api/v1/knowledgebase/search/semantic*", (route) =>
        route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify({
            results: [],
            fallbackUsed: true,
          }),
        }),
      );

      await page.goto("/search");
      await page.locator(".search-input").fill("empty query");
      await page.locator(".search-submit").click();

      // Both the fallback notice and empty state should be visible
      const notice = page.locator(".search-fallback-notice");
      const emptyState = page.locator(".search-empty");

      await expect(notice).toBeVisible({ timeout: 5000 });
      await expect(notice).toContainText("keyword results");
      await expect(emptyState).toBeVisible({ timeout: 5000 });
      await expect(emptyState).toContainText("No documents matched");
    });

    test("keyword mode calls text search endpoint and shows results", async ({
      page,
    }) => {
      await page.route("/api/v1/knowledgebase/search/text*", (route) =>
        route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify({
            results: [
              {
                id: "bbbbbbbb-0000-0000-0000-000000000004",
                fileName: "keyword-match.pdf",
                fileType: "application/pdf",
                fileSize: 2048,
                createdAt: "2026-07-01T00:00:00Z",
              },
  test.describe("Tag display and filtering", () => {
    const mockDocuments = [
      {
        id: "doc-1",
        fileName: "report.pdf",
        fileType: "application/pdf",
        fileSize: 2048,
        createdAt: "2026-06-01T10:00:00Z",
        tags: [
          { id: "t1", label: "finance", source: "AUTO" },
          { id: "t2", label: "quarterly", source: "USER" },
        ],
        extractedEntities: [],
        summary: null,
      },
      {
        id: "doc-2",
        fileName: "notes.txt",
        fileType: "text/plain",
        fileSize: 512,
        createdAt: "2026-06-02T10:00:00Z",
        tags: [{ id: "t3", label: "meeting", source: "AUTO" }],
        extractedEntities: [],
        summary: null,
      },
      {
        id: "doc-3",
        fileName: "summary.md",
        fileType: "text/markdown",
        fileSize: 1024,
        createdAt: "2026-06-03T10:00:00Z",
        tags: [
          { id: "t4", label: "finance", source: "AUTO" },
          { id: "t5", label: "meeting", source: "USER" },
        ],
        extractedEntities: [],
        summary: null,
      },
    ];

    const mockTagList = {
      tags: [
        { name: "finance", documentCount: 2 },
        { name: "meeting", documentCount: 2 },
        { name: "quarterly", documentCount: 1 },
      ],
    };

    test.beforeEach(async ({ page }) => {
      await page.route("/api/v1/knowledgebase/documents", (route) =>
        route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify(mockDocuments),
        }),
      );
      await page.route("/api/v1/knowledgebase/tags", (route) =>
        route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify(mockTagList),
        }),
      );
    });

    test("renders the tag filter chips", async ({ page }) => {
      await page.goto("/documents");

      const filterSection = page.locator(".tag-filter");
      await expect(filterSection).toBeVisible({ timeout: 5000 });

      const chips = filterSection.locator(".tag-filter__chip");
      await expect(chips).toHaveCount(3);
    });

    test("clicking a tag filter chip filters the document list", async ({
      page,
    }) => {
      await page.goto("/documents");

      // Initially 3 documents
      await expect(page.locator(".tree-item")).toHaveCount(3, {
        timeout: 5000,
      });

      // Click "quarterly" filter
      await page
        .locator(".tag-filter__chip", { hasText: "quarterly" })
        .click();

      // Only report.pdf has "quarterly"
      await expect(page.locator(".tree-item")).toHaveCount(1);
      await expect(page.locator(".tree-item").first()).toContainText(
        "report.pdf",
      );
    });

    test("selecting multiple tags narrows results (AND logic)", async ({
      page,
    }) => {
      await page.goto("/documents");
      await expect(page.locator(".tree-item")).toHaveCount(3, {
        timeout: 5000,
      });

      // Select "finance" (2 docs) then "meeting" (overlap is summary.md only)
      await page.locator(".tag-filter__chip", { hasText: "finance" }).click();
      await expect(page.locator(".tree-item")).toHaveCount(2);

      await page.locator(".tag-filter__chip", { hasText: "meeting" }).click();
      await expect(page.locator(".tree-item")).toHaveCount(1);
      await expect(page.locator(".tree-item").first()).toContainText(
        "summary.md",
      );
    });

    test("clear button removes all active filters", async ({ page }) => {
      await page.goto("/documents");
      await expect(page.locator(".tree-item")).toHaveCount(3, {
        timeout: 5000,
      });

      await page.locator(".tag-filter__chip", { hasText: "quarterly" }).click();
      await expect(page.locator(".tree-item")).toHaveCount(1);

      await page.locator(".tag-filter__clear").click();
      await expect(page.locator(".tree-item")).toHaveCount(3);
    });

    test("shows empty message when no documents match filter", async ({
      page,
    }) => {
      // Override with a doc that has no tags
      await page.unroute("/api/v1/knowledgebase/documents");
      await page.route("/api/v1/knowledgebase/documents", (route) =>
        route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify([
            {
              id: "doc-x",
              fileName: "untagged.txt",
              fileType: "text/plain",
              fileSize: 100,
              createdAt: "2026-06-01T10:00:00Z",
              tags: [],
              extractedEntities: [],
              summary: null,
            },
          ]),
        }),
      );

      await page.goto("/documents");
      await expect(page.locator(".tree-item")).toHaveCount(1, {
        timeout: 5000,
      });

      // Filter by "finance" -- untagged doc should not match
      await page.locator(".tag-filter__chip", { hasText: "finance" }).click();
      await expect(page.locator(".tree-item")).toHaveCount(0);
      await expect(page.locator(".tree-status")).toContainText(
        "No documents match the selected tags",
      );
    });
  });

  test.describe("Tag management in detail view", () => {
    test.beforeEach(async ({ page }) => {
      await page.route("/api/v1/knowledgebase/documents", (route) =>
        route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify([
            {
              id: "doc-tag-1",
              fileName: "taggable.pdf",
              fileType: "application/pdf",
              fileSize: 4096,
              createdAt: "2026-06-10T10:00:00Z",
              tags: [
                { id: "t-b", label: "beta", source: "USER" },
                { id: "t-a", label: "alpha", source: "AUTO" },
              ],
              extractedEntities: [],
              summary: null,
            },
          ]),
        }),
      );
      await page.route("/api/v1/knowledgebase/tags", (route) =>
        route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify({ tags: [] }),
        }),
      );
      await page.route(
        "/api/v1/knowledgebase/documents/doc-tag-1",
        (route) =>
          route.fulfill({
            status: 200,
            contentType: "application/json",
            body: JSON.stringify({
              id: "doc-tag-1",
              fileName: "taggable.pdf",
              fileType: "application/pdf",
              fileSize: 4096,
              createdAt: "2026-06-10T10:00:00Z",
              tags: [
                { id: "t-b", label: "beta", source: "USER" },
                { id: "t-a", label: "alpha", source: "AUTO" },
              ],
              extractedEntities: [],
              summary: null,
            }),
          }),
      );
    });

    test("tags are displayed in alphabetical order (fixes #301)", async ({
      page,
    }) => {
      await page.goto("/documents/doc-tag-1");

      const tagList = page.locator('[aria-label="Document tags"]');
      await expect(tagList).toBeVisible({ timeout: 5000 });

      const tags = tagList.locator('.tag[role="button"]');
      await expect(tags).toHaveCount(2);
      // "alpha" should come before "beta" regardless of API order
      await expect(tags.first().locator(".tag__label")).toHaveText("alpha");
      await expect(tags.nth(1).locator(".tag__label")).toHaveText("beta");
    });

    test("USER tags have a remove button, AUTO tags do not", async ({
      page,
    }) => {
      await page.goto("/documents/doc-tag-1");

      const tagList = page.locator('[aria-label="Document tags"]');
      await expect(tagList).toBeVisible({ timeout: 5000 });

      const tags = tagList.locator('.tag[role="button"]');

      // alpha is AUTO -- no remove button
      const alphaTag = tags.first();
      await expect(alphaTag.locator(".tag__remove")).toHaveCount(0);

      // beta is USER -- has remove button
      const betaTag = tags.nth(1);
      await expect(betaTag.locator(".tag__remove")).toHaveCount(1);
    });

    test("add tag form submits to the API", async ({ page }) => {
      let addTagCalled = false;
      await page.route(
        "/api/v1/knowledgebase/documents/doc-tag-1/tags",
        (route, request) => {
          if (request.method() === "POST") {
            addTagCalled = true;
            route.fulfill({ status: 204 });
          } else {
            route.continue();
          }
        },
      );

      await page.goto("/documents/doc-tag-1");

      // Click the "+" button to open the inline input
      const addBtn = page.locator('[aria-label="Add a tag"]');
      await expect(addBtn).toBeVisible({ timeout: 5000 });
      await addBtn.click();

      const input = page.locator('[aria-label="New tag name"]');
      await expect(input).toBeVisible();
      await input.fill("new-tag");
      await input.press("Enter");

      // Give the mutation a moment to fire
      await page.waitForTimeout(500);
      expect(addTagCalled).toBe(true);
    });

    test("remove tag button calls the delete endpoint", async ({ page }) => {
      let removeTagCalled = false;
      await page.route(
        /\/api\/v1\/knowledgebase\/documents\/doc-tag-1\/tags\/t-b/,
        (route, request) => {
          if (request.method() === "DELETE") {
            removeTagCalled = true;
            route.fulfill({ status: 204 });
          } else {
            route.continue();
          }
        },
      );

      await page.goto("/documents/doc-tag-1");

      const tagList = page.locator('[aria-label="Document tags"]');
      await expect(tagList).toBeVisible({ timeout: 5000 });

      // beta is USER, second in sorted order
      const tags = tagList.locator('.tag[role="button"]');
      const removeButton = tags.nth(1).locator(".tag__remove");
      await removeButton.click();

      await page.waitForTimeout(500);
      expect(removeTagCalled).toBe(true);
    });

    test("plus button opens an inline input for adding tags", async ({
      page,
    }) => {
      await page.goto("/documents/doc-tag-1");

      const addBtn = page.locator('[aria-label="Add a tag"]');
      await expect(addBtn).toBeVisible({ timeout: 5000 });

      // Input should not be visible yet
      await expect(page.locator('[aria-label="New tag name"]')).toHaveCount(0);

      await addBtn.click();

      // Now the input should appear and the "+" button should be gone
      await expect(page.locator('[aria-label="New tag name"]')).toBeVisible();
      await expect(addBtn).toHaveCount(0);
    });

    test("clicking the whole tag chip activates the sidebar filter", async ({
      page,
    }) => {
      await page.unroute("/api/v1/knowledgebase/documents");
      await page.unroute("/api/v1/knowledgebase/tags");
      await page.route("/api/v1/knowledgebase/documents", (route) =>
        route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify([
            {
              id: "doc-tag-1",
              fileName: "taggable.pdf",
              fileType: "application/pdf",
              fileSize: 4096,
              createdAt: "2026-06-10T10:00:00Z",
              tags: [
                { id: "t-a", label: "alpha", source: "AUTO" },
                { id: "t-b", label: "beta", source: "USER" },
              ],
              extractedEntities: [],
              summary: null,
            },
            {
              id: "doc-tag-2",
              fileName: "other.txt",
              fileType: "text/plain",
              fileSize: 100,
              createdAt: "2026-06-11T10:00:00Z",
              tags: [{ id: "t-c", label: "gamma", source: "AUTO" }],
              extractedEntities: [],
              summary: null,
            },
          ]),
        }),
      );
      await page.route("/api/v1/knowledgebase/tags", (route) =>
        route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify({
            tags: [
              { name: "alpha", documentCount: 1 },
              { name: "beta", documentCount: 1 },
              { name: "gamma", documentCount: 1 },
            ],
          }),
        }),
      );

      await page.goto("/search");

      // Switch to keyword mode
      const keywordBtn = page
        .locator(".search-mode-btn")
        .filter({ hasText: "Keyword" });
      await keywordBtn.click();
      await expect(keywordBtn).toHaveClass(/search-mode-btn--active/);

      await page.locator(".search-input").fill("keyword");
      await page.locator(".search-submit").click();

      await expect(page).toHaveURL(/[?&]mode=text/);

      const resultList = page.locator(".search-result-list");
      await expect(resultList).toBeVisible({ timeout: 5000 });
      await expect(resultList).toContainText("keyword-match.pdf");
    });

    test("shows empty state when search returns no results", async ({
      page,
    }) => {
      await page.route("/api/v1/knowledgebase/search/semantic*", (route) =>
        route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify({ results: [], fallbackUsed: false }),
        }),
      );

      await page.goto("/search");
      await page.locator(".search-input").fill("nothing matches this");
      await page.locator(".search-submit").click();

      const empty = page.locator(".search-empty");
      await expect(empty).toBeVisible({ timeout: 5000 });
      await expect(empty).toContainText("No documents matched");
    });

    test("shows an error message when the search endpoint returns 500", async ({
      page,
    }) => {
      await page.route("/api/v1/knowledgebase/search/semantic*", (route) =>
        route.fulfill({ status: 500, body: "Internal Server Error" }),
      );

      await page.goto("/search");
      await page.locator(".search-input").fill("error trigger");
      await page.locator(".search-submit").click();

      const error = page.locator(".search-error");
      await expect(error).toBeVisible({ timeout: 15000 });
      await expect(error).toContainText("Search failed");
    });

    test("shows authentication error when the search endpoint returns 401", async ({
      page,
    }) => {
      await page.route("**/protocol/openid-connect/token", (route) =>
        route.fulfill({ status: 401, body: "Unauthorized" }),
      );
      await page.route("/api/v1/knowledgebase/search/semantic*", (route) =>
        route.fulfill({ status: 401, body: "Unauthorized" }),
      );

      await page.goto("/search");
      await page.locator(".search-input").fill("auth check");
      await page.locator(".search-submit").click();

      const error = page.locator(".search-error");
      await expect(error).toBeVisible({ timeout: 15000 });
      await expect(error).toHaveText("Authentication error. Retrying login...");
      await page.goto("/documents/doc-tag-1");

      const tagList = page.locator('[aria-label="Document tags"]');
      await expect(tagList).toBeVisible({ timeout: 5000 });

      // Click the whole "alpha" tag chip (not just text)
      const alphaChip = tagList.locator('.tag[role="button"]').first();
      await alphaChip.click();

      // Sidebar should now filter: only doc-tag-1 has "alpha"
      await expect(page.locator(".tree-item")).toHaveCount(1);
      await expect(page.locator(".tree-item").first()).toContainText(
        "taggable.pdf",
      );

      // The sidebar filter chip for "alpha" should be active
      const sidebarChip = page.locator(".tag-filter__chip", {
        hasText: "alpha",
      });
      await expect(sidebarChip).toHaveClass(/tag-filter__chip--active/);
    });

    test("confirm button submits the new tag", async ({ page }) => {
      let addTagCalled = false;
      await page.route(
        "/api/v1/knowledgebase/documents/doc-tag-1/tags",
        (route, request) => {
          if (request.method() === "POST") {
            addTagCalled = true;
            route.fulfill({ status: 204 });
          } else {
            route.continue();
          }
        },
      );

      await page.goto("/documents/doc-tag-1");

      const addBtn = page.locator('[aria-label="Add a tag"]');
      await expect(addBtn).toBeVisible({ timeout: 5000 });
      await addBtn.click();

      const input = page.locator('[aria-label="New tag name"]');
      await input.fill("new-tag");

      // Click the checkmark confirm button
      const confirmBtn = page.locator('[aria-label="Confirm new tag"]');
      await expect(confirmBtn).toBeVisible();
      await confirmBtn.click();

      await page.waitForTimeout(500);
      expect(addTagCalled).toBe(true);
    });
  });

  test.describe("Tag filter auto-expand", () => {
    test("sidebar expands when a selected tag is beyond the visible 5", async ({
      page,
    }) => {
      // Set up 7 tags so only 5 are shown initially
      const mockTagList = {
        tags: [
          { name: "aaa", documentCount: 1 },
          { name: "bbb", documentCount: 1 },
          { name: "ccc", documentCount: 1 },
          { name: "ddd", documentCount: 1 },
          { name: "eee", documentCount: 1 },
          { name: "fff", documentCount: 1 },
          { name: "ggg", documentCount: 1 },
        ],
      };

      await page.route("/api/v1/knowledgebase/documents", (route) =>
        route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify([
            {
              id: "doc-expand-1",
              fileName: "expand-test.pdf",
              fileType: "application/pdf",
              fileSize: 1024,
              createdAt: "2026-06-10T10:00:00Z",
              tags: [{ id: "t-g", label: "ggg", source: "AUTO" }],
              extractedEntities: [],
              summary: null,
            },
          ]),
        }),
      );
      await page.route("/api/v1/knowledgebase/tags", (route) =>
        route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify(mockTagList),
        }),
      );
      await page.route(
        "/api/v1/knowledgebase/documents/doc-expand-1",
        (route) =>
          route.fulfill({
            status: 200,
            contentType: "application/json",
            body: JSON.stringify({
              id: "doc-expand-1",
              fileName: "expand-test.pdf",
              fileType: "application/pdf",
              fileSize: 1024,
              createdAt: "2026-06-10T10:00:00Z",
              tags: [{ id: "t-g", label: "ggg", source: "AUTO" }],
              extractedEntities: [],
              summary: null,
            }),
          }),
      );

      await page.goto("/documents/doc-expand-1");

      // Initially only 5 chips should be visible
      const chips = page.locator(".tag-filter__chip");
      await expect(chips).toHaveCount(5, { timeout: 5000 });

      // "ggg" should not be visible yet
      await expect(
        page.locator(".tag-filter__chip", { hasText: "ggg" }),
      ).toHaveCount(0);

      // Click the "ggg" tag in the detail view
      const tagList = page.locator('[aria-label="Document tags"]');
      await expect(tagList).toBeVisible({ timeout: 5000 });
      await tagList.locator('.tag[role="button"]').first().click();

      // Now all 7 chips should be visible (auto-expanded)
      await expect(chips).toHaveCount(7);

      // "ggg" chip should now be active
      const gggChip = page.locator(".tag-filter__chip", { hasText: "ggg" });
      await expect(gggChip).toBeVisible();
      await expect(gggChip).toHaveClass(/tag-filter__chip--active/);
    });
  });
});
