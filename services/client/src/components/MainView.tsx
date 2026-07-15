import React, { useMemo, useState } from "react";
import { NavLink, Outlet } from "react-router";
import { useAuth } from "react-oidc-context";
import DocumentTree from "./DocumentTree";
import $api from "../api/client";

export interface MainViewContext {
  onToggleTag: (tagName: string) => void;
}

export default function MainView() {
  const auth = useAuth();
  const [selectedTags, setSelectedTags] = useState<string[]>([]);

  const {
    data: documents,
    isLoading: documentsLoading,
    error: documentsError,
  } = $api.useQuery("get", "/api/v1/knowledgebase/documents");

  const { data: tagsData } = $api.useQuery(
    "get",
    "/api/v1/knowledgebase/tags",
  );

  const allTags = useMemo(
    () =>
      [...(tagsData?.tags ?? [])].sort((a, b) =>
        (a.name ?? "").localeCompare(b.name ?? ""),
      ),
    [tagsData],
  );

  // Client-side filter: only show documents matching ALL selected tags
  const filteredDocuments = useMemo(() => {
    if (!documents) return undefined;
    if (selectedTags.length === 0) return documents;
    return documents.filter((doc) => {
      const docTagLabels = (doc.tags ?? []).map((t) => t.label ?? "");
      return selectedTags.every((tag) => docTagLabels.includes(tag));
    });
  }, [documents, selectedTags]);

  function handleToggleTag(tagName: string) {
    setSelectedTags((prev) =>
      prev.includes(tagName)
        ? prev.filter((t) => t !== tagName)
        : [...prev, tagName],
    );
  }

  function handleClearTags() {
    setSelectedTags([]);
  }

  return (
    <div className="app">
      <header className="app-header">
        <div className="app-header-top">
          <h1>Alexandria</h1>
          <nav className="app-header-nav">
            <NavLink
              to="/ask"
              className={({ isActive }) =>
                `app-tab${isActive ? " app-tab--active" : ""}`
              }
            >
              <span className="app-tab__label">Ask</span>
              <span className="app-tab__sizer">Ask</span>
            </NavLink>
            <NavLink
              to="/search"
              className={({ isActive }) =>
                `app-tab${isActive ? " app-tab--active" : ""}`
              }
            >
              <span className="app-tab__label">Search</span>
              <span className="app-tab__sizer">Search</span>
            </NavLink>
            <NavLink
              to="/documents"
              className={({ isActive }) =>
                `app-tab${isActive ? " app-tab--active" : ""}`
              }
            >
              <span className="app-tab__label">Documents</span>
              <span className="app-tab__sizer">Documents</span>
            </NavLink>
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
          documents={filteredDocuments}
          isLoading={documentsLoading}
          error={documentsError}
          allTags={allTags}
          selectedTags={selectedTags}
          onToggleTag={handleToggleTag}
          onClearTags={handleClearTags}
        />
        <main className="app-main">
          <Outlet context={{ onToggleTag: handleToggleTag } satisfies MainViewContext} />
        </main>
      </div>
    </div>
  );
}
