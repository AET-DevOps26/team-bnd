import React, { useState } from "react";
import { useAuth } from "react-oidc-context";
import DocumentTree from "./components/DocumentTree";
import DocumentDetail from "./components/DocumentDetail";

export default function App() {
  const auth = useAuth();
  const [selectedDocumentId, setSelectedDocumentId] = useState<string | null>(
    null,
  );

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

  if (!auth.isAuthenticated) {
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

  return (
    <div className="app">
      <header className="app-header">
        <div className="app-header-top">
          <h1>Alexandria</h1>
          <div className="user-info">
            <span className="user-name">
              {auth.user?.profile.preferred_username ?? auth.user?.profile.sub}
            </span>
            <button className="logout-button" onClick={() => auth.removeUser()}>
              Logout
            </button>
          </div>
        </div>
      </header>
      <div className="app-body">
        <DocumentTree
          selectedId={selectedDocumentId}
          onSelect={setSelectedDocumentId}
        />
        <main className="app-main">
          <DocumentDetail documentId={selectedDocumentId} />
        </main>
      </div>
    </div>
  );
}
