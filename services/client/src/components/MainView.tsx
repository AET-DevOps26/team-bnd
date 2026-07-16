import React, { useEffect, useMemo, useState } from "react";
import { NavLink, Outlet, useLocation } from "react-router";
import { useAuth } from "react-oidc-context";
import DocumentTree, { type EntityGroup } from "./DocumentTree";
import $api from "../api/client";
import { isProcessing, pollWhileProcessing } from "../utils/documentStatus";

const ENTITY_TYPE_ORDER = ["PERSON", "ORGANIZATION", "TOPIC", "DATE"] as const;

export interface MainViewContext {
  onToggleTag: (tagName: string) => void;
}

export default function MainView() {
  const auth = useAuth();
  const location = useLocation();
  const [selectedTags, setSelectedTags] = useState<string[]>([]);
  const [selectedEntities, setSelectedEntities] = useState<string[]>([]);
  const [lastDocumentId, setLastDocumentId] = useState<string | null>(null);

  // Remember the last opened document so the Documents tab can reopen it.
  useEffect(() => {
    const match = location.pathname.match(/^\/documents\/(.+)$/);
    if (match?.[1]) setLastDocumentId(match[1]);
  }, [location.pathname]);

  const {
    data: documents,
    isLoading: documentsLoading,
    error: documentsError,
  } = $api.useQuery("get", "/api/v1/knowledgebase/documents", undefined, {
    refetchInterval: (query) => {
      const docs = query.state.data;
      if (!docs) return false;
      return pollWhileProcessing(
        docs.some(isProcessing),
        query.state.dataUpdateCount,
      );
    },
  });

  const anyProcessing = (documents ?? []).some(isProcessing);

  // Tags are generated asynchronously, so keep the aggregate list fresh while
  // any document is still processing.
  const { data: tagsData } = $api.useQuery(
    "get",
    "/api/v1/knowledgebase/tags",
    undefined,
    { refetchInterval: anyProcessing ? 3000 : false },
  );

  const allTags = useMemo(
    () =>
      [...(tagsData?.tags ?? [])].sort((a, b) =>
        (a.name ?? "").localeCompare(b.name ?? ""),
      ),
    [tagsData],
  );

  // Distinct entity names per type with the number of documents each appears in,
  // derived from the loaded documents since the backend has no aggregated-entity
  // endpoint. Nested map: type -> name -> set of document ids.
  const entityGroups = useMemo<EntityGroup[]>(() => {
    const byType = new Map<string, Map<string, Set<string>>>();
    for (const doc of documents ?? []) {
      if (!doc.id) continue;
      const seen = new Set<string>();
      for (const entity of doc.extractedEntities ?? []) {
        if (!entity.type || !entity.name) continue;
        const dedupeKey = `${entity.type}:${entity.name}`;
        if (seen.has(dedupeKey)) continue;
        seen.add(dedupeKey);
        const names = byType.get(entity.type) ?? new Map<string, Set<string>>();
        const docIds = names.get(entity.name) ?? new Set<string>();
        docIds.add(doc.id);
        names.set(entity.name, docIds);
        byType.set(entity.type, names);
      }
    }
    return ENTITY_TYPE_ORDER.flatMap((type) => {
      const names = byType.get(type);
      if (!names) return [];
      const items = [...names.entries()]
        .map(([name, docIds]) => ({ name, documentCount: docIds.size }))
        .sort((a, b) => a.name.localeCompare(b.name));
      return [{ type, names: items }];
    });
  }, [documents]);

  // Client-side filter: match ALL selected tags AND all selected entities
  const filteredDocuments = useMemo(() => {
    if (!documents) return undefined;
    if (selectedTags.length === 0 && selectedEntities.length === 0)
      return documents;
    return documents.filter((doc) => {
      const docTagLabels = (doc.tags ?? []).map((t) => t.label ?? "");
      const docEntityNames = (doc.extractedEntities ?? []).map(
        (e) => e.name ?? "",
      );
      return (
        selectedTags.every((tag) => docTagLabels.includes(tag)) &&
        selectedEntities.every((name) => docEntityNames.includes(name))
      );
    });
  }, [documents, selectedTags, selectedEntities]);

  function handleToggleTag(tagName: string) {
    setSelectedTags((prev) =>
      prev.includes(tagName)
        ? prev.filter((t) => t !== tagName)
        : [...prev, tagName],
    );
  }

  function handleToggleEntity(name: string) {
    setSelectedEntities((prev) =>
      prev.includes(name) ? prev.filter((e) => e !== name) : [...prev, name],
    );
  }

  function handleClearTags() {
    setSelectedTags([]);
    setSelectedEntities([]);
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
              to={lastDocumentId ? `/documents/${lastDocumentId}` : "/documents"}
              className={`app-tab${location.pathname.startsWith("/documents") ? " app-tab--active" : ""}`}
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
          entityGroups={entityGroups}
          selectedEntities={selectedEntities}
          onToggleTag={handleToggleTag}
          onToggleEntity={handleToggleEntity}
          onClearTags={handleClearTags}
        />
        <main className="app-main">
          <Outlet context={{ onToggleTag: handleToggleTag } satisfies MainViewContext} />
        </main>
      </div>
    </div>
  );
}
