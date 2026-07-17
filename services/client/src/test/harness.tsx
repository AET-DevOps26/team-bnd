import type { ReactElement, ReactNode } from "react";
import { render } from "@testing-library/react";
import { MemoryRouter, Routes, Route, Outlet } from "react-router";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { MainViewContext } from "../components/MainView";

function makeClient() {
  return new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
}

// Wrap a component in the providers it needs (react-query + router). `route`
// sets the initial history entry so components reading the URL see it.
export function renderWithProviders(ui: ReactElement, route = "/") {
  return render(
    <QueryClientProvider client={makeClient()}>
      <MemoryRouter initialEntries={[route]}>{ui}</MemoryRouter>
    </QueryClientProvider>,
  );
}

// DocumentDetail reads route params and the outlet context provided by MainView.
// This mounts it under a matching route with a parent Outlet that supplies the
// context, so useParams/useOutletContext resolve the same way they do in the app.
export function renderDocumentDetail(
  element: ReactNode,
  { id, onToggleTag = () => undefined }: { id?: string; onToggleTag?: (t: string) => void },
) {
  const entry = id ? `/documents/${id}` : "/documents";
  return render(
    <QueryClientProvider client={makeClient()}>
      <MemoryRouter initialEntries={[entry]}>
        <Routes>
          <Route
            element={<Outlet context={{ onToggleTag } satisfies MainViewContext} />}
          >
            <Route path="documents" element={element} />
            <Route path="documents/:id" element={element} />
          </Route>
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}
