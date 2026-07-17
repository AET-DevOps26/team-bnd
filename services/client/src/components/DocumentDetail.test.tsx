import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { screen, cleanup, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import DocumentDetail from "./DocumentDetail";
import { renderDocumentDetail } from "../test/harness";
import * as clientModule from "../api/client";
import type { components } from "../api/schema";

vi.mock("../api/client");

const api = clientModule as unknown as typeof import("../api/__mocks__/client");

type Document = components["schemas"]["Document"];

const DOC = "/api/v1/knowledgebase/documents/{id}";
const DOWNLOAD = "/api/v1/knowledgebase/documents/{id}/download";
const ADD_TAG = "/api/v1/knowledgebase/documents/{id}/tags";
const REMOVE_TAG =
  "/api/v1/knowledgebase/documents/{documentId}/tags/{tagId}";
const REPROCESS_SUMMARY =
  "/api/v1/knowledgebase/documents/{id}/reprocess/summary";
const REPROCESS_TAGS = "/api/v1/knowledgebase/documents/{id}/reprocess/tags";
const REPROCESS_ENTITIES =
  "/api/v1/knowledgebase/documents/{id}/reprocess/entities";

function baseDoc(overrides: Partial<Document> = {}): Document {
  return {
    id: "doc-1",
    fileName: "report.pdf",
    fileType: "text/plain",
    fileSize: 1024,
    createdAt: "2024-06-15T12:00:00Z",
    tags: [],
    extractedEntities: [],
    ...overrides,
  };
}

function loadedDoc(overrides: Partial<Document> = {}) {
  api.setQuery("get", DOC, { data: baseDoc(overrides) });
}

const originalCreateObjectURL = URL.createObjectURL;
const originalRevokeObjectURL = URL.revokeObjectURL;

beforeEach(() => {
  api.resetApiMock();
  URL.createObjectURL = vi.fn(() => "blob:mock");
  URL.revokeObjectURL = vi.fn();
});
afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
  // Direct global assignments aren't undone by restoreAllMocks; put the
  // jsdom originals back so other suites see a clean URL.
  URL.createObjectURL = originalCreateObjectURL;
  URL.revokeObjectURL = originalRevokeObjectURL;
});

describe("DocumentDetail", () => {
  it("prompts to pick a document when no id is set", () => {
    renderDocumentDetail(<DocumentDetail />, {});
    expect(
      screen.getByText("Select a document to view its contents."),
    ).toBeInTheDocument();
  });

  it("shows a loading state", () => {
    api.setQuery("get", DOC, { isLoading: true });
    renderDocumentDetail(<DocumentDetail />, { id: "doc-1" });
    expect(screen.getByText("Loading…")).toBeInTheDocument();
  });

  it.each([
    ["NOT_AUTHENTICATED", "Authentication error. Retrying login..."],
    ["FORBIDDEN", "You do not have permission to view this document."],
    ["NOT_FOUND", "This document no longer exists."],
    ["boom", "Failed to load document."],
  ])("maps the %s error to a message", (code, message) => {
    api.setQuery("get", DOC, { error: new Error(code) });
    renderDocumentDetail(<DocumentDetail />, { id: "doc-1" });
    expect(screen.getByText(message)).toBeInTheDocument();
  });

  it("renders a fully-processed plain-text document", () => {
    loadedDoc({
      fileType: "text/plain",
      tags: [
        { id: "t1", label: "ml", source: "AUTO" },
        { id: "t2", label: "mine", source: "USER" },
      ],
      extractedEntities: [{ id: "e1", name: "Ada", type: "PERSON" }],
      summary: { status: "COMPLETED", content: "A short summary.", modelUsed: "gpt" },
      entitiesStatus: "COMPLETED",
      tagsStatus: "COMPLETED",
    });
    api.setQuery("get", DOWNLOAD, { data: "the plain text body" });
    renderDocumentDetail(<DocumentDetail />, { id: "doc-1" });

    expect(screen.getByRole("heading", { name: "report.pdf" })).toBeInTheDocument();
    expect(screen.getByText("A short summary.")).toBeInTheDocument();
    expect(screen.getByText("Ada")).toBeInTheDocument();
    expect(screen.getByText("ml")).toBeInTheDocument();
    expect(screen.getByText("the plain text body")).toBeInTheDocument();
  });

  it("forwards a tag click to the outlet handler", async () => {
    const user = userEvent.setup();
    const onToggleTag = vi.fn();
    loadedDoc({ tags: [{ id: "t1", label: "ml", source: "AUTO" }] });
    renderDocumentDetail(<DocumentDetail />, { id: "doc-1", onToggleTag });
    await user.click(screen.getByRole("button", { name: "ml" }));
    expect(onToggleTag).toHaveBeenCalledWith("ml");
  });

  it("removes a user tag", async () => {
    const user = userEvent.setup();
    loadedDoc({ tags: [{ id: "t2", label: "mine", source: "USER" }] });
    renderDocumentDetail(<DocumentDetail />, { id: "doc-1" });
    await user.click(screen.getByRole("button", { name: "Remove tag mine" }));
    expect(api.mutateSpy("delete", REMOVE_TAG)).toHaveBeenCalledOnce();
  });

  it("adds a tag via the input", async () => {
    const user = userEvent.setup();
    loadedDoc({});
    renderDocumentDetail(<DocumentDetail />, { id: "doc-1" });
    await user.click(screen.getByRole("button", { name: "Add a tag" }));
    await user.type(screen.getByLabelText("New tag name"), "fresh{Enter}");
    expect(api.mutateSpy("post", ADD_TAG)).toHaveBeenCalledOnce();
  });

  it("shows an error when adding a tag fails", async () => {
    const user = userEvent.setup();
    loadedDoc({});
    api.setMutation("post", ADD_TAG, { isError: true });
    renderDocumentDetail(<DocumentDetail />, { id: "doc-1" });
    await user.click(screen.getByRole("button", { name: "Add a tag" }));
    expect(screen.getByText("Failed to add tag.")).toBeInTheDocument();
  });

  it("renames the document", async () => {
    const user = userEvent.setup();
    loadedDoc({ fileName: "old.pdf" });
    renderDocumentDetail(<DocumentDetail />, { id: "doc-1" });
    await user.click(screen.getByRole("button", { name: "Rename document" }));
    const input = screen.getByLabelText("Document name");
    await user.clear(input);
    await user.type(input, "new.pdf{Enter}");
    expect(api.mutateSpy("patch", DOC)).toHaveBeenCalledOnce();
  });

  it("deletes the document after confirmation", async () => {
    const user = userEvent.setup();
    vi.spyOn(window, "confirm").mockReturnValue(true);
    loadedDoc({});
    renderDocumentDetail(<DocumentDetail />, { id: "doc-1" });
    await user.click(screen.getByRole("button", { name: "Delete document" }));
    expect(api.mutateSpy("delete", DOC)).toHaveBeenCalledOnce();
  });

  it("does not delete when the confirmation is dismissed", async () => {
    const user = userEvent.setup();
    vi.spyOn(window, "confirm").mockReturnValue(false);
    loadedDoc({});
    renderDocumentDetail(<DocumentDetail />, { id: "doc-1" });
    await user.click(screen.getByRole("button", { name: "Delete document" }));
    expect(api.mutateSpy("delete", DOC)).not.toHaveBeenCalled();
  });

  it("triggers reprocessing for each section", async () => {
    const user = userEvent.setup();
    loadedDoc({
      tags: [{ id: "t1", label: "ml", source: "AUTO" }],
      extractedEntities: [{ id: "e1", name: "Ada", type: "PERSON" }],
      summary: { status: "COMPLETED", content: "s", modelUsed: "m" },
      tagsStatus: "COMPLETED",
      entitiesStatus: "COMPLETED",
    });
    renderDocumentDetail(<DocumentDetail />, { id: "doc-1" });

    const section = (name: string) =>
      screen.getByRole("heading", { name }).closest("section") as HTMLElement;

    await user.click(
      within(section("Summary")).getByRole("button", { name: /Regenerate/ }),
    );
    await user.click(
      within(section("Tags")).getByRole("button", { name: /Regenerate/ }),
    );
    await user.click(
      within(section("Extracted Entities")).getByRole("button", {
        name: /Regenerate/,
      }),
    );

    expect(api.mutateSpy("post", REPROCESS_SUMMARY)).toHaveBeenCalledOnce();
    expect(api.mutateSpy("post", REPROCESS_TAGS)).toHaveBeenCalledOnce();
    expect(api.mutateSpy("post", REPROCESS_ENTITIES)).toHaveBeenCalledOnce();
  });

  it("shows failure states for each pipeline step", () => {
    loadedDoc({
      tagsStatus: "FAILED",
      entitiesStatus: "FAILED",
      summary: { status: "FAILED" },
    });
    renderDocumentDetail(<DocumentDetail />, { id: "doc-1" });
    expect(screen.getByText("Tag generation failed.")).toBeInTheDocument();
    expect(screen.getByText("Entity extraction failed.")).toBeInTheDocument();
    expect(
      screen.getByText(/Summary generation failed/),
    ).toBeInTheDocument();
  });

  it("renders a pdf preview", () => {
    loadedDoc({ fileType: "application/pdf" });
    api.setQuery("get", DOWNLOAD, { data: "pdf-bytes" });
    renderDocumentDetail(<DocumentDetail />, { id: "doc-1" });
    expect(
      screen.getByTitle("PDF preview of report.pdf"),
    ).toBeInTheDocument();
  });

  it("shows a pdf preview error", () => {
    loadedDoc({ fileType: "application/pdf" });
    api.setQuery("get", DOWNLOAD, { isError: true });
    renderDocumentDetail(<DocumentDetail />, { id: "doc-1" });
    expect(screen.getByText("Failed to load PDF preview.")).toBeInTheDocument();
  });

  it("renders a markdown preview", () => {
    loadedDoc({ fileType: "text/markdown" });
    api.setQuery("get", DOWNLOAD, { data: "# Big Heading" });
    renderDocumentDetail(<DocumentDetail />, { id: "doc-1" });
    expect(
      screen.getByRole("heading", { name: "Big Heading" }),
    ).toBeInTheDocument();
  });

  it("shows a markdown preview error", () => {
    loadedDoc({ fileType: "text/markdown" });
    api.setQuery("get", DOWNLOAD, { isError: true });
    renderDocumentDetail(<DocumentDetail />, { id: "doc-1" });
    expect(
      screen.getByText("Failed to load markdown preview."),
    ).toBeInTheDocument();
  });
});
