import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { screen, cleanup, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import MainView from "./MainView";
import { renderWithProviders } from "../test/harness";
import * as clientModule from "../api/client";
import type { components } from "../api/schema";

vi.mock("../api/client");

// The mocked "../api/client" is the same instance the component imports, so the
// test drives it through the extra control exports the manual mock adds.
const api = clientModule as unknown as typeof import("../api/__mocks__/client");

const removeUser = vi.hoisted(() => vi.fn());
const authMock = vi.hoisted(() => ({ value: {} as Record<string, unknown> }));
vi.mock("react-oidc-context", () => ({ useAuth: () => authMock.value }));

type Document = components["schemas"]["Document"];

const DOCS = "/api/v1/knowledgebase/documents";
const TAGS = "/api/v1/knowledgebase/tags";

function doc(overrides: Partial<Document>): Document {
  return { id: crypto.randomUUID(), fileName: "file.pdf", ...overrides };
}

beforeEach(() => {
  api.resetApiMock();
  authMock.value = {
    isAuthenticated: true,
    isLoading: false,
    user: { profile: { preferred_username: "ada", sub: "s1" } },
    removeUser,
  };
  removeUser.mockReset();
});
afterEach(cleanup);

describe("MainView", () => {
  it("renders the header, username and navigation tabs", () => {
    renderWithProviders(<MainView />, "/documents");
    expect(
      screen.getByRole("heading", { name: "Alexandria" }),
    ).toBeInTheDocument();
    expect(screen.getByText("ada")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Ask/ })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Search/ })).toBeInTheDocument();
  });

  it("lists documents and tag chips from the loaded data", () => {
    api.setQuery("get", DOCS, {
      data: [doc({ fileName: "alpha.pdf" }), doc({ fileName: "beta.pdf" })],
    });
    api.setQuery("get", TAGS, {
      data: { tags: [{ name: "ml", documentCount: 1 }] },
    });
    renderWithProviders(<MainView />, "/documents");
    expect(screen.getByText("alpha.pdf")).toBeInTheDocument();
    expect(screen.getByText("beta.pdf")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /ml/ })).toBeInTheDocument();
  });

  it("derives entity filters from the documents", () => {
    api.setQuery("get", DOCS, {
      data: [
        doc({
          fileName: "a.pdf",
          extractedEntities: [{ type: "PERSON", name: "Ada" }],
        }),
      ],
    });
    renderWithProviders(<MainView />, "/documents");
    expect(
      screen.getByRole("button", { name: /Filter by entity/ }),
    ).toBeInTheDocument();
  });

  it("filters the document list when a tag is selected", async () => {
    const user = userEvent.setup();
    api.setQuery("get", DOCS, {
      data: [
        doc({ fileName: "tagged.pdf", tags: [{ label: "ml" }] }),
        doc({ fileName: "untagged.pdf", tags: [] }),
      ],
    });
    api.setQuery("get", TAGS, {
      data: { tags: [{ name: "ml", documentCount: 1 }] },
    });
    renderWithProviders(<MainView />, "/documents");

    expect(screen.getByText("untagged.pdf")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: /ml/ }));
    await waitFor(() =>
      expect(screen.queryByText("untagged.pdf")).not.toBeInTheDocument(),
    );
    expect(screen.getByText("tagged.pdf")).toBeInTheDocument();
  });

  it("logs the user out", async () => {
    const user = userEvent.setup();
    renderWithProviders(<MainView />, "/documents");
    await user.click(screen.getByRole("button", { name: "Logout" }));
    expect(removeUser).toHaveBeenCalledOnce();
  });

  it("filters by entity and then clears the selection", async () => {
    const user = userEvent.setup();
    api.setQuery("get", DOCS, {
      data: [
        doc({
          fileName: "has-ada.pdf",
          // duplicate entity exercises the de-dup branch
          extractedEntities: [
            { type: "PERSON", name: "Ada" },
            { type: "PERSON", name: "Ada" },
          ],
        }),
        doc({ fileName: "no-ada.pdf", extractedEntities: [] }),
      ],
    });
    renderWithProviders(<MainView />, "/documents");

    await user.click(screen.getByRole("button", { name: /Filter by entity/ }));
    await user.click(screen.getByRole("button", { name: /Ada/ }));
    await waitFor(() =>
      expect(screen.queryByText("no-ada.pdf")).not.toBeInTheDocument(),
    );
    expect(screen.getByText("has-ada.pdf")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Clear" }));
    await waitFor(() =>
      expect(screen.getByText("no-ada.pdf")).toBeInTheDocument(),
    );
  });

  it("remembers the last opened document for the Documents tab", () => {
    renderWithProviders(<MainView />, "/documents/xyz");
    expect(screen.getByRole("link", { name: /Documents/ })).toHaveAttribute(
      "href",
      "/documents/xyz",
    );
  });
});
