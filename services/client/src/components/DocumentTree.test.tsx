import React from "react";
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen, cleanup } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Routes, Route } from "react-router";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import DocumentTree from "./DocumentTree";
import * as clientModule from "../api/client";
import type { components } from "../api/schema";

vi.mock("../api/client");

const api = clientModule as unknown as typeof import("../api/__mocks__/client");

type Document = components["schemas"]["Document"];
type TreeProps = React.ComponentProps<typeof DocumentTree>;

const DELETE_DOC = "/api/v1/knowledgebase/documents/{id}";

function makeDoc(overrides: Partial<Document> = {}): Document {
  return {
    id: crypto.randomUUID(),
    fileName: "report.pdf",
    createdAt: "2024-06-15T12:00:00Z",
    ...overrides,
  };
}

function renderTree(
  overrides: Partial<TreeProps> = {},
  selectedId?: string,
) {
  const props: TreeProps = {
    documents: [],
    isLoading: false,
    error: null,
    allTags: [],
    selectedTags: [],
    entityGroups: [],
    selectedEntities: [],
    onToggleTag: vi.fn(),
    onToggleEntity: vi.fn(),
    onClearTags: vi.fn(),
    ...overrides,
  };
  const entry = selectedId ? `/documents/${selectedId}` : "/documents";
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  const utils = render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[entry]}>
        <Routes>
          <Route path="documents" element={<DocumentTree {...props} />} />
          <Route path="documents/:id" element={<DocumentTree {...props} />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
  return { ...utils, props };
}

beforeEach(() => {
  api.resetApiMock();
});
afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

describe("DocumentTree", () => {
  it("shows a loading status", () => {
    renderTree({ documents: undefined, isLoading: true });
    expect(screen.getByText("Loading…")).toBeInTheDocument();
  });

  it("maps errors to messages", () => {
    renderTree({ documents: undefined, error: new Error("boom") });
    expect(screen.getByText("Failed to load documents.")).toBeInTheDocument();
  });

  it("shows a permission message on FORBIDDEN", () => {
    renderTree({ documents: undefined, error: new Error("FORBIDDEN") });
    expect(
      screen.getByText("You do not have permission to view documents."),
    ).toBeInTheDocument();
  });

  it("shows a retry message on NOT_AUTHENTICATED", () => {
    renderTree({ documents: undefined, error: new Error("NOT_AUTHENTICATED") });
    expect(
      screen.getByText("Authentication error. Retrying login..."),
    ).toBeInTheDocument();
  });

  it("shows the empty state", () => {
    renderTree({ documents: [] });
    expect(screen.getByText("No documents yet.")).toBeInTheDocument();
  });

  it("reports when filters exclude everything", () => {
    renderTree({ documents: [], selectedTags: ["ml"] });
    expect(
      screen.getByText("No documents match the selected filters."),
    ).toBeInTheDocument();
  });

  it("lists documents and flags processing ones", () => {
    renderTree({
      documents: [
        makeDoc({ fileName: "done.pdf" }),
        makeDoc({ fileName: "busy.pdf", summary: { status: "PENDING" } }),
      ],
    });
    expect(screen.getByText("done.pdf")).toBeInTheDocument();
    expect(screen.getByText("busy.pdf")).toBeInTheDocument();
    expect(screen.getAllByRole("status")).toHaveLength(1);
  });

  it("reports tag clicks and clears filters", async () => {
    const user = userEvent.setup();
    const onToggleTag = vi.fn();
    const onClearTags = vi.fn();
    renderTree({
      allTags: [{ name: "machine learning", documentCount: 3 }],
      selectedTags: ["machine learning"],
      onToggleTag,
      onClearTags,
    });
    await user.click(screen.getByRole("button", { name: /machine learning/ }));
    expect(onToggleTag).toHaveBeenCalledWith("machine learning");
    await user.click(screen.getByRole("button", { name: "Clear" }));
    expect(onClearTags).toHaveBeenCalledOnce();
  });

  it("expands the entity filter and reports entity clicks", async () => {
    const user = userEvent.setup();
    const onToggleEntity = vi.fn();
    renderTree({
      entityGroups: [
        { type: "PERSON", names: [{ name: "Ada", documentCount: 2 }] },
      ],
      onToggleEntity,
    });
    await user.click(screen.getByRole("button", { name: /Filter by entity/ }));
    await user.click(screen.getByRole("button", { name: /Ada/ }));
    expect(onToggleEntity).toHaveBeenCalledOnce();
  });

  it("keeps the entity filter visible when there are no entities", async () => {
    const user = userEvent.setup();
    renderTree({ documents: [], entityGroups: [] });
    const toggle = screen.getByRole("button", { name: /Filter by entity/ });
    expect(toggle).toBeInTheDocument();
    await user.click(toggle);
    expect(
      screen.getByText(
        "Entity filtering becomes available once entities are extracted.",
      ),
    ).toBeInTheDocument();
  });

  it("expands a long tag list on demand", async () => {
    const user = userEvent.setup();
    const allTags = Array.from({ length: 7 }, (_, i) => ({
      name: `tag-${i}`,
      documentCount: 1,
    }));
    renderTree({ allTags });
    expect(screen.queryByText("tag-6")).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "+2 more" }));
    expect(screen.getByText("tag-6")).toBeInTheDocument();
  });

  it("keeps a selected hidden tag visible without expanding", () => {
    const allTags = Array.from({ length: 7 }, (_, i) => ({
      name: `tag-${i}`,
      documentCount: 1,
    }));
    renderTree({ allTags, selectedTags: ["tag-6"] });
    expect(screen.getByRole("button", { name: /tag-6/ })).toBeInTheDocument();
  });

  it("deletes a document after confirmation", async () => {
    const user = userEvent.setup();
    vi.spyOn(window, "confirm").mockReturnValue(true);
    const doc = makeDoc({ fileName: "gone.pdf" });
    renderTree({ documents: [doc] }, doc.id);
    await user.click(screen.getByRole("button", { name: "Delete gone.pdf" }));
    expect(api.mutateSpy("delete", DELETE_DOC)).toHaveBeenCalledOnce();
  });

  it("does not delete when confirmation is dismissed", async () => {
    const user = userEvent.setup();
    vi.spyOn(window, "confirm").mockReturnValue(false);
    const doc = makeDoc({ fileName: "safe.pdf" });
    renderTree({ documents: [doc] });
    await user.click(screen.getByRole("button", { name: "Delete safe.pdf" }));
    expect(api.mutateSpy("delete", DELETE_DOC)).not.toHaveBeenCalled();
  });
});
