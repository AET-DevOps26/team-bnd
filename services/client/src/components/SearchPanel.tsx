import React, { useEffect, useMemo, useState } from "react";
import { Link, useSearchParams } from "react-router";
import { useQueryClient } from "@tanstack/react-query";
import $api from "../api/client";
import type { components } from "../api/schema";
import { formatBytes, formatDateTime } from "../utils/format";

type SemanticSearchResultDto = components["schemas"]["SemanticSearchResultDto"];
type DocumentRefDto = components["schemas"]["DocumentRefDto"];

/**
 * Maps a fetch error to a user-facing message, following the same pattern used
 * in QAPanel and DocumentDetail.
 */
function errorMessage(
  err: unknown,
  messages: { notAuthenticated: string; forbidden: string; fallback: string },
): string {
  const code = err instanceof Error ? err.message : String(err);
  if (code === "NOT_AUTHENTICATED") return messages.notAuthenticated;
  if (code === "FORBIDDEN") return messages.forbidden;
  return messages.fallback;
}

function formatFileType(mimeType: string | undefined): string {
  if (!mimeType) return "";
  const known: Record<string, string> = {
    "application/pdf": "PDF",
    "text/plain": "Text",
    "text/markdown": "Markdown",
    "application/msword": "Word",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document":
      "Word",
  };
  return known[mimeType] ?? mimeType.split("/")[1]?.toUpperCase() ?? mimeType;
}

interface TextResultListProps {
  results: DocumentRefDto[];
}

function TextResultList({ results }: TextResultListProps) {
  if (results.length === 0) {
    return <p className="search-empty">No documents matched.</p>;
  }

  return (
    <ul className="search-result-list" aria-label="Text search results">
      {results.map((doc) => {
        const type = formatFileType(doc.fileType);
        const size = doc.fileSize != null ? formatBytes(doc.fileSize) : null;
        const meta = [type, size].filter(Boolean).join(", ");
        return (
          <li
            key={doc.id}
            className="search-result-item search-result-item--text"
          >
            <div className="search-result-header">
              <Link
                to={`/documents/${doc.id}`}
                className="search-result-title"
                title={doc.fileName}
              >
                {doc.fileName}
              </Link>
            </div>
            {meta && <p className="search-result-meta">{meta}</p>}
          </li>
        );
      })}
    </ul>
  );
}

interface SemanticResultListProps {
  results: SemanticSearchResultDto[];
  fallbackUsed: boolean;
}

function SemanticResultList({
  results,
  fallbackUsed,
}: SemanticResultListProps) {
  if (results.length === 0) {
    if (fallbackUsed) {
      return (
        <>
          <p className="search-fallback-notice">
            Vector search unavailable -- showing keyword results instead.
          </p>
          <p className="search-empty">No documents matched.</p>
        </>
      );
    }
    return <p className="search-empty">No documents matched.</p>;
  }

  return (
    <>
      {fallbackUsed && (
        <p className="search-fallback-notice">
          Vector search unavailable -- showing keyword results instead.
        </p>
      )}
      <ul className="search-result-list" aria-label="Semantic search results">
        {results.map((result, index) => {
          const doc = result.document;
          const score =
            result.score != null ? Math.round(result.score * 100) : null;
          return (
            <li
              key={doc?.id ?? index}
              className="search-result-item search-result-item--semantic"
            >
              <div className="search-result-header">
                <Link
                  to={`/documents/${doc?.id}`}
                  className="search-result-title"
                  title={doc?.fileName}
                >
                  {doc?.fileName ?? "Untitled document"}
                </Link>
                {score != null && (
                  <span
                    className="search-result-score"
                    title="Relevance score"
                    aria-label={`Relevance: ${score}%`}
                  >
                    {score}%
                  </span>
                )}
              </div>
              {result.snippet && (
                <p className="search-result-snippet">{result.snippet}</p>
              )}
            </li>
          );
        })}
      </ul>
    </>
  );
}

type SearchMode = "semantic" | "text";

function isSearchMode(value: string | null): value is SearchMode {
  return value === "semantic" || value === "text";
}

export default function SearchPanel() {
  const [searchParams, setSearchParams] = useSearchParams();
  const queryClient = useQueryClient();

  const urlQuery = searchParams.get("q") ?? "";
  const urlModeRaw = searchParams.get("mode");
  const urlMode: SearchMode = isSearchMode(urlModeRaw)
    ? urlModeRaw
    : "semantic";

  // inputValue tracks what's currently typed; initialised from the URL so
  // the field is pre-filled when the user navigates back to a previous search.
  const [inputValue, setInputValue] = useState(urlQuery);

  // Keep inputValue in sync if the URL changes externally (e.g. back/forward).
  useEffect(() => {
    setInputValue(urlQuery);
  }, [urlQuery]);

  // submittedQuery and mode are derived from the URL, not local state.
  const submittedQuery = urlQuery;
  const mode = urlMode;

  function commitSearch(query: string, nextMode: SearchMode) {
    const trimmed = query.trim();
    setSearchParams({ q: trimmed, mode: nextMode }, { replace: false });
  }

  function refreshHistory() {
    void queryClient.invalidateQueries({
      queryKey: ["get", "/api/v1/knowledgebase/history/search"],
    });
  }

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    commitSearch(inputValue, mode);
  }

  function handleModeChange(next: SearchMode) {
    // Switching mode re-runs the search for the current committed query.
    if (submittedQuery) {
      setSearchParams({ q: submittedQuery, mode: next }, { replace: true });
    } else {
      setSearchParams(
        (prev) => {
          const p = new URLSearchParams(prev);
          p.set("mode", next);
          return p;
        },
        { replace: true },
      );
    }
  }

  // Semantic search -- fires when submittedQuery is non-empty
  const {
    data: semanticData,
    isLoading: semanticLoading,
    error: semanticError,
  } = $api.useQuery(
    "get",
    "/api/v1/knowledgebase/search/semantic",
    { params: { query: { q: submittedQuery, limit: 10 } } },
    { enabled: mode === "semantic" && submittedQuery.length > 0 },
  );

  // Text search -- fires when submittedQuery is non-empty
  const {
    data: textData,
    isLoading: textLoading,
    error: textError,
  } = $api.useQuery(
    "get",
    "/api/v1/knowledgebase/search/text",
    { params: { query: { q: submittedQuery } } },
    { enabled: mode === "text" && submittedQuery.length > 0 },
  );

  const isLoading = mode === "semantic" ? semanticLoading : textLoading;
  const error = mode === "semantic" ? semanticError : textError;

  const { data: historyData } = $api.useQuery(
    "get",
    "/api/v1/knowledgebase/history/search",
  );

  // Drop empty queries and collapse repeats so a term searched several times
  // only shows its most recent entry.
  const history = useMemo(() => {
    const seen = new Set<string>();
    return (historyData ?? []).filter((entry) => {
      const key = entry.queryText?.trim();
      if (!key || seen.has(key)) return false;
      seen.add(key);
      return true;
    });
  }, [historyData]);

  const { mutate: clearHistory, isPending: isClearing } = $api.useMutation(
    "delete",
    "/api/v1/knowledgebase/history/search",
    { onSuccess: refreshHistory },
  );

  const settled = !isLoading && !error && submittedQuery.length > 0;
  useEffect(() => {
    if (settled) refreshHistory();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [settled, semanticData, textData]);

  const errorMsg = error
    ? errorMessage(error, {
        notAuthenticated: "Authentication error. Retrying login...",
        forbidden: "You do not have permission to search.",
        fallback: "Search failed. Please try again.",
      })
    : null;

  return (
    <section className="search-panel">
      <form className="search-form" onSubmit={handleSubmit} role="search">
        <label htmlFor="search-input" className="search-form-label">
          Search documents
        </label>
        <div className="search-input-row">
          <input
            id="search-input"
            type="search"
            className="search-input"
            value={inputValue}
            onChange={(e) => setInputValue(e.target.value)}
            placeholder="Search by keyword or meaning..."
            disabled={isLoading}
            aria-label="Search query"
          />
          <button
            type="submit"
            className="search-submit"
            disabled={
              isLoading || (submittedQuery.length > 0 && !inputValue.trim())
            }
          >
            {isLoading ? "Searching..." : "Search"}
          </button>
        </div>
        <div
          className="search-mode-toggle"
          role="group"
          aria-label="Search mode"
        >
          <button
            type="button"
            className={`search-mode-btn${mode === "semantic" ? " search-mode-btn--active" : ""}`}
            onClick={() => handleModeChange("semantic")}
          >
            Semantic
          </button>
          <button
            type="button"
            className={`search-mode-btn${mode === "text" ? " search-mode-btn--active" : ""}`}
            onClick={() => handleModeChange("text")}
          >
            Keyword
          </button>
        </div>
        <p className="search-mode-hint">
          {mode === "semantic"
            ? "Semantic search finds documents by meaning, so related results surface even when they don't share your exact words."
            : "Keyword search matches your exact words against filenames and document text."}
        </p>
      </form>

      {errorMsg && <p className="search-error">{errorMsg}</p>}

      <div className="search-results">
        {submittedQuery.length === 0 && (
          <p className="search-empty">Enter a query above to search.</p>
        )}

        {isLoading && <p className="search-status">Searching...</p>}

        {!isLoading && !error && submittedQuery.length > 0 && (
          <>
            {mode === "semantic" && semanticData?.results != null && (
              <SemanticResultList
                results={semanticData.results}
                fallbackUsed={semanticData.fallbackUsed ?? false}
              />
            )}
            {mode === "text" && textData?.results != null && (
              <TextResultList results={textData.results} />
            )}
          </>
        )}
      </div>

      {history.length > 0 && (
        <div className="search-history">
          <div className="search-history__head">
            <span className="search-history__label">Recent searches</span>
            <button
              type="button"
              className="search-history__clear"
              disabled={isClearing}
              onClick={() => {
                if (!confirm("Clear search history?")) return;
                clearHistory({});
              }}
            >
              {isClearing ? "Clearing..." : "Clear"}
            </button>
          </div>
          <ul className="search-history__list">
            {history.map((entry) => (
              <li key={entry.queryText} className="search-history__item">
                <button
                  type="button"
                  className="search-history__query"
                  onClick={() => {
                    setInputValue(entry.queryText ?? "");
                    commitSearch(entry.queryText ?? "", mode);
                  }}
                  title={entry.queryText}
                >
                  {entry.queryText}
                </button>
                <span className="search-history__meta">
                  {entry.resultCount ?? 0} results
                  {entry.timestamp && ` · ${formatDateTime(entry.timestamp)}`}
                </span>
              </li>
            ))}
          </ul>
        </div>
      )}
    </section>
  );
}
