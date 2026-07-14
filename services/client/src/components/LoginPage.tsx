import React from "react";
import { Navigate } from "react-router";
import { useAuth } from "react-oidc-context";

export default function LoginPage() {
  const auth = useAuth();

  if (auth.isAuthenticated) {
    return <Navigate to="/ask" replace />;
  }

  if (auth.isLoading) {
    return (
      <div className="app">
        <div className="login-view">
          <p>Loading...</p>
        </div>
      </div>
    );
  }

  if (auth.error) {
    return (
      <div className="app">
        <div className="login-view">
          <h1>Alexandria</h1>
          <p className="login-error">
            Authentication error: {auth.error.message}
          </p>
          <button
            className="login-button"
            onClick={() => auth.signinRedirect()}
          >
            Try again
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="app">
      <div className="login-view">
        <h1>Alexandria — Document Summarization</h1>
        <p>
          Alexandria helps users upload documents and get concise summaries,
          extracted tags, and searchable knowledge — so reading a 40-page
          report is no longer necessary.
        </p>
        <button
          className="login-button"
          onClick={() => auth.signinRedirect()}
        >
          Login
        </button>
      </div>
    </div>
  );
}
