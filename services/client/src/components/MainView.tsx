import React, { useState } from "react";
import { useAuth } from "react-oidc-context";
import DocumentTree from "./DocumentTree";
import DocumentDetail from "./DocumentDetail";
import $api from "../api/client";
import QAPanel from "./QAPanel";

type AppTab = "documents" | "qa";

export default function MainView() {
  const auth = useAuth();
  const [selectedDocumentId, setSelectedDocumentId] = useState<string | null>(
    null,
  );
  const [activeTab, setActiveTab] = useState<AppTab>("qa");

  const {
    data: documents,
    isLoading: documentsLoading,
    error: documentsError,
  } = $api.useQuery("get", "/api/v1/knowledgebase/documents");

  function handleSelectDocument(id: string) {
    setSelectedDocumentId(id);
    setActiveTab("documents");
  }

  return (
    <div className="app">
      <header className="app-header">
        <div className="app-header-top">
          <h1>Alexandria</h1>
          <nav className="app-header-nav">
            <button
              className={`app-tab${activeTab === "qa" ? " app-tab--active" : ""}`}
              onClick={() => setActiveTab("qa")}
            >
              <span className="app-tab__label">Ask</span>
              <span className="app-tab__sizer">Ask</span>
            </button>
            <button
              className={`app-tab${activeTab === "documents" ? " app-tab--active" : ""}`}
              onClick={() => setActiveTab("documents")}
            >
              <span className="app-tab__label">Documents</span>
              <span className="app-tab__sizer">Documents</span>
            </button>
          </nav>
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
          onSelect={handleSelectDocument}
          documents={documents}
          isLoading={documentsLoading}
          error={documentsError}
        />
        <main className="app-main">
          <div
            id="tab-panel-documents"
            className="app-tab-content"
            hidden={activeTab !== "documents"}
          >
            <DocumentDetail documentId={selectedDocumentId} />
          </div>
          <div
            id="tab-panel-qa"
            className="app-tab-content"
            hidden={activeTab !== "qa"}
          >
            <QAPanel
              documents={documents ?? []}
              onSelectDocument={handleSelectDocument}
            />
          </div>
        </main>
      </div>
    </div>
  );
}
