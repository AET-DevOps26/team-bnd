import React from "react";
import { useAuth } from "react-oidc-context";

export default function App() {
  const auth = useAuth();

  if (auth.isLoading) {
    return (
      <div className="app">
        <p>Loading...</p>
      </div>
    );
  }

  if (auth.error) {
    return (
      <div className="app">
        <p>Authentication error: {auth.error.message}</p>
        <button onClick={() => auth.signinRedirect()}>Try again</button>
      </div>
    );
  }

  if (!auth.isAuthenticated) {
    return (
      <div className="app">
        <h1>Alexandria — Document Summarization</h1>
        <p>
          Alexandria helps users upload documents and get concise summaries,
          extracted tags, and searchable knowledge — so reading a 40-page report
          is no longer necessary.
        </p>
        <button onClick={() => auth.signinRedirect()}>Login</button>
      </div>
    );
  }

  return (
    <div className="app">
      <header className="app-header">
        <h1>Alexandria — Document Summarization</h1>
        <div className="user-info">
          <span>
            Logged in as{" "}
            <strong>
              {auth.user?.profile.preferred_username ?? auth.user?.profile.sub}
            </strong>
          </span>
          <button onClick={() => auth.removeUser()}>Logout</button>
        </div>
      </header>
      <p>
        Alexandria helps users upload documents and get concise summaries,
        extracted tags, and searchable knowledge — so reading a 40-page report
        is no longer necessary.
      </p>
    </div>
  );
}
