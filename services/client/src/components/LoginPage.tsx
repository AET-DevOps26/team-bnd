import React from "react";
import { Navigate } from "react-router";
import { useAuth } from "react-oidc-context";
import ArchiveScene from "./ArchiveScene";

export default function LoginPage() {
  const auth = useAuth();

  if (auth.isAuthenticated) {
    return <Navigate to="/ask" replace />;
  }

  if (auth.isLoading) {
    return (
      <div className="login-page">
        <div className="login-panel">
          <p className="login-loading">Loading…</p>
        </div>
      </div>
    );
  }

  return (
    <div className="login-page">
      <div className="login-panel">
        <div className="login-content">
          <span className="login-brand">Alexandria</span>
          <h1 className="login-title">
            Every document, distilled to what matters.
          </h1>
          <p className="login-lede">
            Upload research papers, reports, and notes. Alexandria summarizes
            them, extracts the key people, dates, and topics, and lets you ask
            questions instead of reading forty pages to find one answer.
          </p>

          {auth.error ? (
            <>
              <p className="login-error" role="alert">
                Authentication error: {auth.error.message}
              </p>
              <button
                className="login-button"
                onClick={() => auth.signinRedirect()}
              >
                Try again
              </button>
            </>
          ) : (
            <button
              className="login-button"
              onClick={() => auth.signinRedirect()}
            >
              Enter the library
            </button>
          )}

          <ul className="login-highlights">
            <li>
              <span className="login-highlights__mark" aria-hidden="true" />
              Auto-generated summaries
            </li>
            <li>
              <span className="login-highlights__mark" aria-hidden="true" />
              Extracted entities and tags
            </li>
            <li>
              <span className="login-highlights__mark" aria-hidden="true" />
              Ask questions across your knowledge base
            </li>
          </ul>
        </div>
      </div>

      <aside className="login-stage" aria-hidden="true">
        <ArchiveScene />
      </aside>
    </div>
  );
}
