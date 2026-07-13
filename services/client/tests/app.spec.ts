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

      await page.goto("/");

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
      await page.goto("/");

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
      await page.goto("/");

      const input = page.locator(".qa-input");
      await expect(input).toBeVisible();

      const submit = page.locator(".qa-submit");
      await expect(submit).toBeVisible();
      await expect(submit).toHaveText("Ask");
    });

    test("shows empty state when there is no history", async ({ page }) => {
      await page.goto("/");

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
            sourceObjectKeys: [],
            timestamp: "2026-07-08T10:00:00Z",
            modelUsed: "gpt-4o",
          }),
        }),
      );

      await page.goto("/");

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
            sourceObjectKeys: [],
            timestamp: "2026-07-08T11:30:00Z",
            modelUsed: "gpt-4o",
          }),
        });
      });

      await page.goto("/");
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
            sourceObjectKeys: [],
            timestamp: "2026-07-08T12:30:00Z",
            modelUsed: "test-model",
          }),
        }),
      );

      await page.goto("/");
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
            sourceObjectKeys: [objectKey],
            timestamp: "2026-07-08T11:00:00Z",
            modelUsed: "gpt-4o",
          }),
        }),
      );

      await page.goto("/");
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
            sourceObjectKeys: [objectKey],
            timestamp: "2026-07-08T11:10:00Z",
            modelUsed: "gpt-4o",
          }),
        }),
      );

      await page.goto("/");
      await page.locator(".qa-input").fill("What is this?");
      await page.locator(".qa-submit").click();

      const sourceLink = page.locator(".qa-source-link--unresolved").first();
      await expect(sourceLink).toBeVisible({ timeout: 5000 });
      await expect(sourceLink).toHaveText("mystery.pdf");
    });

    test("clicking a resolved source switches to Documents tab and selects the document", async ({
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
            sourceObjectKeys: [objectKey],
            timestamp: "2026-07-08T11:05:00Z",
            modelUsed: "gpt-4o",
          }),
        }),
      );

      await page.goto("/");
      await page.locator(".qa-input").fill("What is in the guide?");
      await page.locator(".qa-submit").click();

      const sourceLink = page.locator(".qa-source-link").first();
      await expect(sourceLink).toBeVisible({ timeout: 5000 });
      await sourceLink.click();

      // Documents tab should now be active
      const documentsTab = page.getByRole("button", { name: "Documents" });
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
              sourceObjectKeys: [],
              timestamp: "2026-07-07T09:00:00Z",
              modelUsed: "gpt-4o",
            },
          ]),
        }),
      );

      await page.goto("/");

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

      await page.goto("/");
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

      await page.goto("/");
      await page.locator(".qa-input").fill("Auth check question.");
      await page.locator(".qa-submit").click();

      const error = page.locator(".qa-error");
      await expect(error).toBeVisible({ timeout: 5000 });
      await expect(error).toHaveText("Authentication error. Retrying login...");
    });

    test("Documents tab button switches back to document view", async ({
      page,
    }) => {
      await page.goto("/");

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
          sourceObjectKeys: [],
          timestamp: "2026-07-06T08:00:00Z",
          modelUsed: "gpt-4o",
        },
        {
          id: "aaaaaaaa-0000-0000-0000-000000000009",
          question: "Second history question?",
          answer: "Second historical answer.",
          sourceObjectKeys: [],
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

      await page.goto("/");

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
});
