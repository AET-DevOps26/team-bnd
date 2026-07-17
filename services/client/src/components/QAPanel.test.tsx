import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { screen, cleanup } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import QAPanel from "./QAPanel";
import { renderWithProviders } from "../test/harness";
import * as clientModule from "../api/client";
import type { components } from "../api/schema";

vi.mock("../api/client");

const api = clientModule as unknown as typeof import("../api/__mocks__/client");

type QAInteraction = components["schemas"]["QAInteraction"];

const DOCS = "/api/v1/knowledgebase/documents";
const HISTORY = "/api/v1/qa/history";
const ASK = "/api/v1/qa/ask";

const seeded: QAInteraction = {
  id: "i1",
  question: "What is the finding?",
  answer: "The finding is 42.",
  timestamp: "2024-06-15T12:00:00Z",
  modelUsed: "gpt-test",
  citations: [
    { marker: 1, documentId: "d1", fileName: "doc1.pdf", snippet: "s1" },
    { marker: 2, fileName: "loose.pdf" },
    { marker: 3, objectKey: "users/x/documents/d3/file3.pdf" },
    { marker: 4, objectKey: "a/b/c/last.pdf" },
  ],
};

beforeEach(() => {
  api.resetApiMock();
});
afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

describe("QAPanel", () => {
  it("shows a loading state while history loads", () => {
    api.setQuery("get", HISTORY, { isLoading: true });
    renderWithProviders(<QAPanel />, "/ask");
    expect(screen.getByText("Loading history…")).toBeInTheDocument();
  });

  it("shows a permission error when history is forbidden", () => {
    api.setQuery("get", HISTORY, { error: new Error("FORBIDDEN") });
    renderWithProviders(<QAPanel />, "/ask");
    expect(
      screen.getByText("You do not have permission to view Q&A history."),
    ).toBeInTheDocument();
  });

  it("shows the empty state with no questions", () => {
    renderWithProviders(<QAPanel />, "/ask");
    expect(
      screen.getByText("No questions yet. Ask something above."),
    ).toBeInTheDocument();
  });

  it("seeds the answer and citations from history", () => {
    api.setQuery("get", DOCS, {
      data: [
        {
          id: "d3",
          fileName: "file3.pdf",
          objectKey: "users/x/documents/d3/file3.pdf",
        },
      ],
    });
    api.setQuery("get", HISTORY, { data: [seeded] });
    renderWithProviders(<QAPanel />, "/ask");

    expect(
      screen.getByRole("button", { name: "What is the finding?" }),
    ).toBeInTheDocument();
    expect(screen.getByText("The finding is 42.")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "doc1.pdf" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "file3.pdf" })).toBeInTheDocument();
    expect(screen.getByText("loose.pdf")).toBeInTheDocument();
    expect(screen.getByText("last.pdf")).toBeInTheDocument();
    expect(screen.getByText("Model: gpt-test")).toBeInTheDocument();
  });

  it("hides the open answer when Hide is clicked", async () => {
    const user = userEvent.setup();
    api.setQuery("get", HISTORY, { data: [seeded] });
    renderWithProviders(<QAPanel />, "/ask");
    expect(screen.getByText("The finding is 42.")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Hide" }));
    expect(screen.queryByText("The finding is 42.")).not.toBeInTheDocument();
  });

  it("asks a question and shows the new answer", async () => {
    const user = userEvent.setup();
    api.setMutation("post", ASK, {
      result: { id: "i2", question: "Q2", answer: "A fresh answer." },
    });
    renderWithProviders(<QAPanel />, "/ask");

    await user.type(screen.getByRole("textbox"), "Q2");
    await user.click(screen.getByRole("button", { name: "Ask" }));

    expect(api.mutateSpy("post", ASK)).toHaveBeenCalledOnce();
    expect(screen.getByText("A fresh answer.")).toBeInTheDocument();
  });

  it("shows an ask error message", () => {
    api.setMutation("post", ASK, { error: new Error("FORBIDDEN") });
    renderWithProviders(<QAPanel />, "/ask");
    expect(
      screen.getByText("You do not have permission to ask questions."),
    ).toBeInTheDocument();
  });

  it("clears history after confirmation", async () => {
    const user = userEvent.setup();
    vi.spyOn(window, "confirm").mockReturnValue(true);
    api.setQuery("get", HISTORY, { data: [seeded] });
    renderWithProviders(<QAPanel />, "/ask");

    await user.click(screen.getByRole("button", { name: "Clear" }));
    expect(api.mutateSpy("delete", HISTORY)).toHaveBeenCalledOnce();
    expect(
      screen.getByText("No questions yet. Ask something above."),
    ).toBeInTheDocument();
  });
});
