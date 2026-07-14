import React from "react";
import { Routes, Route, Navigate } from "react-router";
import { useAuth } from "react-oidc-context";
import LoginPage from "./components/LoginPage";
import MainView from "./components/MainView";
import DocumentDetail from "./components/DocumentDetail";
import QAPanel from "./components/QAPanel";

function AuthGuard({ children }: { children: React.ReactNode }) {
  const auth = useAuth();
  if (auth.isLoading) return null;
  if (!auth.isAuthenticated) return <Navigate to="/" replace />;
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
