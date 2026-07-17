import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { screen, cleanup, waitFor } from "@testing-library/react";
import App from "./App";
import { renderWithProviders } from "./test/harness";
import { resetApiMock } from "./api/__mocks__/client";

vi.mock("./api/client");

const authMock = vi.hoisted(() => ({ value: {} as Record<string, unknown> }));
vi.mock("react-oidc-context", () => ({ useAuth: () => authMock.value }));

beforeEach(() => {
  authMock.value = {};
  resetApiMock();
});
afterEach(cleanup);

describe("App routing", () => {
  it("shows the login page on the root route when signed out", () => {
    authMock.value = { isAuthenticated: false, isLoading: false, signinRedirect: vi.fn() };
    renderWithProviders(<App />, "/");
    expect(
      screen.getByRole("button", { name: "Enter the library" }),
    ).toBeInTheDocument();
  });

  it("redirects unknown routes back to the login page", () => {
    authMock.value = { isAuthenticated: false, isLoading: false, signinRedirect: vi.fn() };
    renderWithProviders(<App />, "/does-not-exist");
    expect(
      screen.getByRole("button", { name: "Enter the library" }),
    ).toBeInTheDocument();
  });

  it("guards protected routes and triggers a redirect when signed out", async () => {
    const signinRedirect = vi.fn();
    authMock.value = { isAuthenticated: false, isLoading: false, signinRedirect };
    renderWithProviders(<App />, "/ask");
    expect(screen.getByText("Redirecting...")).toBeInTheDocument();
    await waitFor(() => expect(signinRedirect).toHaveBeenCalled());
  });

  it("shows a redirecting placeholder while auth is still loading", () => {
    authMock.value = { isAuthenticated: false, isLoading: true, signinRedirect: vi.fn() };
    renderWithProviders(<App />, "/ask");
    expect(screen.getByText("Redirecting...")).toBeInTheDocument();
  });

  it("renders the protected view once authenticated", () => {
    authMock.value = {
      isAuthenticated: true,
      isLoading: false,
      user: { profile: { preferred_username: "ada" } },
      removeUser: vi.fn(),
      signinRedirect: vi.fn(),
    };
    renderWithProviders(<App />, "/ask");
    expect(screen.getByRole("heading", { name: "Alexandria" })).toBeInTheDocument();
    expect(
      screen.getByText("No questions yet. Ask something above."),
    ).toBeInTheDocument();
  });
});
