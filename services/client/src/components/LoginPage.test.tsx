import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen, cleanup } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Routes, Route } from "react-router";
import LoginPage from "./LoginPage";

const authMock = vi.hoisted(() => ({ value: {} as Record<string, unknown> }));
vi.mock("react-oidc-context", () => ({ useAuth: () => authMock.value }));

function renderLogin() {
  return render(
    <MemoryRouter initialEntries={["/"]}>
      <Routes>
        <Route path="/" element={<LoginPage />} />
        <Route path="/ask" element={<div>ASK PAGE</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

beforeEach(() => {
  authMock.value = {};
});
afterEach(cleanup);

describe("LoginPage", () => {
  it("redirects an authenticated user to the ask page", () => {
    authMock.value = { isAuthenticated: true };
    renderLogin();
    expect(screen.getByText("ASK PAGE")).toBeInTheDocument();
  });

  it("shows a loading state while auth resolves", () => {
    authMock.value = { isAuthenticated: false, isLoading: true };
    renderLogin();
    expect(screen.getByText("Loading…")).toBeInTheDocument();
  });

  it("offers a sign-in button that starts the redirect", async () => {
    const signinRedirect = vi.fn();
    authMock.value = { isAuthenticated: false, isLoading: false, signinRedirect };
    renderLogin();
    await userEvent.click(
      screen.getByRole("button", { name: "Enter the library" }),
    );
    expect(signinRedirect).toHaveBeenCalledOnce();
  });

  it("surfaces an auth error with a retry button", async () => {
    const signinRedirect = vi.fn();
    authMock.value = {
      isAuthenticated: false,
      isLoading: false,
      error: new Error("token expired"),
      signinRedirect,
    };
    renderLogin();
    expect(screen.getByRole("alert")).toHaveTextContent("token expired");
    await userEvent.click(screen.getByRole("button", { name: "Try again" }));
    expect(signinRedirect).toHaveBeenCalledOnce();
  });
});
