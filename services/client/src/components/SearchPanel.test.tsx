import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { screen, cleanup } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import SearchPanel from "./SearchPanel";
import { renderWithProviders } from "../test/harness";
import * as clientModule from "../api/client";

vi.mock("../api/client");

const api = clientModule as unknown as typeof import("../api/__mocks__/client");

const SEMANTIC = "/api/v1/knowledgebase/search/semantic";
const TEXT = "/api/v1/knowledgebase/search/text";
const HISTORY = "/api/v1/knowledgebase/history/search";

beforeEach(() => {
  api.resetApiMock();
});
afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

describe("SearchPanel", () => {
  it("prompts for a query when none is entered", () => {
    renderWithProviders(<SearchPanel />, "/search");
    expect(screen.getByText("Enter a query above to search.")).toBeInTheDocument();
  });

  it("renders semantic results with a relevance score", () => {
    api.setQuery("get", SEMANTIC, {
      data: {
        results: [
          {
            document: { id: "d1", fileName: "alpha.pdf" },
            score: 0.5,
            snippet: "matching snippet",
          },
        ],
        fallbackUsed: false,
      },
    });
    renderWithProviders(<SearchPanel />, "/search?q=ai&mode=semantic");
    expect(screen.getByRole("link", { name: "alpha.pdf" })).toBeInTheDocument();
    expect(screen.getByText("50%")).toBeInTheDocument();
    expect(screen.getByText("matching snippet")).toBeInTheDocument();
  });

  it("shows the fallback notice when vector search is unavailable", () => {
    api.setQuery("get", SEMANTIC, {
      data: { results: [], fallbackUsed: true },
    });
    renderWithProviders(<SearchPanel />, "/search?q=ai&mode=semantic");
    expect(
      screen.getByText(/Vector search unavailable/),
    ).toBeInTheDocument();
    expect(screen.getByText("No documents matched.")).toBeInTheDocument();
  });

  it("renders keyword results with type and size", () => {
    api.setQuery("get", TEXT, {
      data: {
        results: [
          {
            id: "d2",
            fileName: "beta.pdf",
            fileType: "application/pdf",
            fileSize: 2048,
          },
        ],
      },
    });
    renderWithProviders(<SearchPanel />, "/search?q=ai&mode=text");
    expect(screen.getByRole("link", { name: "beta.pdf" })).toBeInTheDocument();
    expect(screen.getByText("PDF, 2.0 KB")).toBeInTheDocument();
  });

  it("shows a searching indicator while loading", () => {
    api.setQuery("get", SEMANTIC, { isLoading: true });
    renderWithProviders(<SearchPanel />, "/search?q=ai&mode=semantic");
    expect(
      screen.getByText("Searching...", { selector: ".search-status" }),
    ).toBeInTheDocument();
  });

  it("shows a permission error when search is forbidden", () => {
    api.setQuery("get", SEMANTIC, { error: new Error("FORBIDDEN") });
    renderWithProviders(<SearchPanel />, "/search?q=ai&mode=semantic");
    expect(
      screen.getByText("You do not have permission to search."),
    ).toBeInTheDocument();
  });

  it("runs a search when the form is submitted", async () => {
    const user = userEvent.setup();
    api.setQuery("get", SEMANTIC, {
      data: {
        results: [{ document: { id: "d9", fileName: "hit.pdf" }, score: 0.9 }],
        fallbackUsed: false,
      },
    });
    renderWithProviders(<SearchPanel />, "/search");
    await user.type(screen.getByRole("searchbox"), "hello");
    await user.click(screen.getByRole("button", { name: "Search" }));
    expect(screen.getByRole("link", { name: "hit.pdf" })).toBeInTheDocument();
  });

  it("switches to keyword mode via the toggle", async () => {
    const user = userEvent.setup();
    renderWithProviders(<SearchPanel />, "/search");
    await user.click(screen.getByRole("button", { name: "Keyword" }));
    expect(
      screen.getByText(/Keyword search matches your exact words/),
    ).toBeInTheDocument();
  });

  it("lists recent searches and re-runs one on click", async () => {
    const user = userEvent.setup();
    api.setQuery("get", HISTORY, {
      data: [
        {
          queryText: "prior query",
          resultCount: 3,
          timestamp: "2024-06-15T12:00:00Z",
        },
      ],
    });
    renderWithProviders(<SearchPanel />, "/search");
    const historyButton = screen.getByRole("button", { name: "prior query" });
    expect(historyButton).toBeInTheDocument();
    await user.click(historyButton);
    expect(screen.getByRole("searchbox")).toHaveValue("prior query");
  });

  it("clears search history after confirmation", async () => {
    const user = userEvent.setup();
    vi.spyOn(window, "confirm").mockReturnValue(true);
    api.setQuery("get", HISTORY, {
      data: [{ queryText: "prior query", resultCount: 1 }],
    });
    renderWithProviders(<SearchPanel />, "/search");
    await user.click(screen.getByRole("button", { name: "Clear" }));
    expect(api.mutateSpy("delete", HISTORY)).toHaveBeenCalledOnce();
  });
});
