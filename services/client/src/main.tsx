import React from "react";
import { createRoot } from "react-dom/client";
import { AuthProvider } from "react-oidc-context";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import App from "./App";
import { oidcConfig } from "./oidcConfig";
import "./styles/index.scss";

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      // Do not retry if we encounter an authentication or permission failure.
      retry: (failureCount, error) => {
        if (
          error instanceof Error &&
          (error.message === "NOT_AUTHENTICATED" ||
            error.message === "FORBIDDEN")
        ) {
          return false;
        }
        return failureCount < 3;
      },
    },
  },
});

const authConfig = {
  ...oidcConfig,
  onSigninCallback: () => {
    // Remove OIDC query params from URL after successful login.
    window.history.replaceState({}, document.title, window.location.pathname);
    queryClient.invalidateQueries();
  },
  onSignoutCallback: () => {
    queryClient.removeQueries();
  },
};

const rootElement = document.getElementById("root");
if (!rootElement) throw new Error("Root element not found");

const root = createRoot(rootElement);
root.render(
  <QueryClientProvider client={queryClient}>
    <AuthProvider {...authConfig}>
      <App />
    </AuthProvider>
  </QueryClientProvider>,
);
