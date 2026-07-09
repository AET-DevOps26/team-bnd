import React, { useState } from "react";
import { useAuth } from "react-oidc-context";
import DocumentTree from "./DocumentTree";
import DocumentDetail from "./DocumentDetail";

export default function MainView() {
  const auth = useAuth();
  const [selectedDocumentId, setSelectedDocumentId] = useState<string | null>(
    null,
  );

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
