import React from "react";
import { describe, it, expect, vi, afterEach } from "vitest";
import { render, screen, cleanup } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import DocumentTree from "./DocumentTree";
import type { components } from "../api/schema";

type Document = components["schemas"]["Document"];
type TreeProps = React.ComponentProps<typeof DocumentTree>;

function makeDoc(overrides: Partial<Document> = {}): Document {
  return {
    id: crypto.randomUUID(),
    fileName: "report.pdf",
    createdAt: "2024-06-15T12:00:00Z",
    ...overrides,
  };
}

function renderTree(overrides: Partial<TreeProps> = {}) {
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
  const queryClient = new QueryClient();
  const utils = render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <DocumentTree {...props} />
      </MemoryRouter>
    </QueryClientProvider>,
  );
  return { ...utils, props };
}

afterEach(cleanup);

describe("DocumentTree", () => {
  it("shows a loading status while documents are loading", () => {
    renderTree({ documents: undefined, isLoading: true });
    expect(screen.getByText("Loading…")).toBeInTheDocument();
  });

  it("shows a generic error message when loading failed", () => {
    renderTree({ documents: undefined, error: new Error("boom") });
    expect(screen.getByText("Failed to load documents.")).toBeInTheDocument();
  });

  it("shows a permission message on a FORBIDDEN error", () => {
    renderTree({ documents: undefined, error: new Error("FORBIDDEN") });
    expect(
      screen.getByText("You do not have permission to view documents."),
    ).toBeInTheDocument();
  });

  it("shows the empty-state hint when there are no documents", () => {
    renderTree({ documents: [] });
    expect(screen.getByText("No documents yet.")).toBeInTheDocument();
  });

  it("tells the user filters matched nothing when filters are active", () => {
    renderTree({ documents: [], selectedTags: ["ml"] });
    expect(
      screen.getByText("No documents match the selected filters."),
    ).toBeInTheDocument();
  });

  it("renders a document list and marks processing documents with a loader", () => {
    renderTree({
      documents: [
        makeDoc({ fileName: "done.pdf" }),
        makeDoc({ fileName: "busy.pdf", summary: { status: "PENDING" } }),
      ],
    });
    expect(screen.getByText("done.pdf")).toBeInTheDocument();
    expect(screen.getByText("busy.pdf")).toBeInTheDocument();
    // The pending document renders a DotsLoader (role="status").
    expect(screen.getAllByRole("status")).toHaveLength(1);
  });

  it("renders tag chips and reports clicks", async () => {
    const user = userEvent.setup();
    const onToggleTag = vi.fn();
    renderTree({
      allTags: [
        { name: "machine learning", documentCount: 3 },
        { name: "devops", documentCount: 1 },
      ],
      onToggleTag,
    });
    await user.click(screen.getByRole("button", { name: /machine learning/ }));
    expect(onToggleTag).toHaveBeenCalledWith("machine learning");
  });

  it("clears active filters via the clear button", async () => {
    const user = userEvent.setup();
    const onClearTags = vi.fn();
    renderTree({ selectedTags: ["devops"], onClearTags });
    await user.click(screen.getByRole("button", { name: "Clear" }));
    expect(onClearTags).toHaveBeenCalledOnce();
  });

  it("shows entity filters once the entity section is expanded", async () => {
    const user = userEvent.setup();
    const onToggleEntity = vi.fn();
    renderTree({
      entityGroups: [
        { type: "PERSON", names: [{ name: "Ada Lovelace", documentCount: 2 }] },
      ],
      onToggleEntity,
    });
    await user.click(screen.getByRole("button", { name: /Filter by entity/ }));
    const chip = screen.getByRole("button", { name: /Ada Lovelace/ });
    await user.click(chip);
    expect(onToggleEntity).toHaveBeenCalledOnce();
  });

  it("expands a long tag list on demand", async () => {
    const user = userEvent.setup();
    const allTags = Array.from({ length: 7 }, (_, i) => ({
      name: `tag-${i}`,
      documentCount: 1,
    }));
    renderTree({ allTags });
    // Only the first five are shown until the "+2 more" toggle is used.
    expect(screen.queryByText("tag-6")).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "+2 more" }));
    expect(screen.getByText("tag-6")).toBeInTheDocument();
  });
});
