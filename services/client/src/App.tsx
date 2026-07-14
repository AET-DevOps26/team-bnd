import React, { useEffect } from "react";
import { Routes, Route } from "react-router";
import { useAuth } from "react-oidc-context";
import LoginPage from "./components/LoginPage";
import MainView from "./components/MainView";
import DocumentDetail from "./components/DocumentDetail";
import QAPanel from "./components/QAPanel";

function AuthGuard({ children }: { children: React.ReactNode }) {
  const auth = useAuth();

  useEffect(() => {
    if (!auth.isLoading && !auth.isAuthenticated) {
      auth.signinRedirect({
        redirect_uri: window.location.href,
      });
    }
  }, [auth, auth.isLoading, auth.isAuthenticated]);

  if (auth.isLoading || !auth.isAuthenticated) return null;
  return <>{children}</>;
}

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<LoginPage />} />
      <Route
        element={
          <AuthGuard>
            <MainView />
          </AuthGuard>
        }
      >
        <Route path="ask" element={<QAPanel />} />
        <Route path="documents" element={<DocumentDetail />} />
        <Route path="documents/:id" element={<DocumentDetail />} />
      </Route>
    </Routes>
  );
}
