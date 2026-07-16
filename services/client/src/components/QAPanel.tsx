import React, { useEffect, useRef, useState } from "react";
import ReactMarkdown from "react-markdown";
import { Link } from "react-router";
import $api from "../api/client";
import type { components } from "../api/schema";
import { useQueryClient } from "@tanstack/react-query";
import { formatDateTime } from "../utils/format";

type QAInteraction = components["schemas"]["QAInteraction"];
type Document = components["schemas"]["Document"];

/**
 * Maps a fetch error to a user-facing message. Extracting the error code and
 * branching is the same for every query; only the wording differs per action,
 * so callers pass the three strings they want to show.
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

/**
 * Extracts the display name for a source reference.
 * Object keys follow the pattern: users/{subject}/documents/{docId}/{filename}
 * Falls back to the raw key if the structure is unexpected.
 */
function sourceKeyToName(key: string): string {
  const parts = key.split("/");
  return parts[parts.length - 1] || key;
}

/**
 * Finds the document in the list whose objectKey matches the given source key.
 * The objectKey stored on the Document entity is the S3 key carried on each citation.
 */
function findDocumentByKey(
  documents: Document[],
  key: string,
): Document | undefined {
  return documents.find((doc) => doc.objectKey === key);
}

export default function QAPanel() {
  const queryClient = useQueryClient();
  const [question, setQuestion] = useState("");
  const [interactions, setInteractions] = useState<QAInteraction[]>([]);
  const [expandedId, setExpandedId] = useState<string | number | null>(null);
  const historyLoaded = useRef(false);

  const { data: documents = [] } = $api.useQuery(
    "get",
    "/api/v1/knowledgebase/documents",
  );

  const {
    data: historyData,
    isLoading: historyLoading,
    error: historyError,
  } = $api.useQuery("get", "/api/v1/qa/history");

  // Seed interactions from history once on mount. Merge instead of overwrite so
  // an answer that was asked while history was still loading isn't dropped when
  // the history request finally resolves.
  useEffect(() => {
    if (historyData && !historyLoaded.current) {
      historyLoaded.current = true;
      setInteractions((prev) => {
        const seen = new Set(prev.map((i) => i.id));
        const merged = [...prev, ...historyData.filter((i) => !seen.has(i.id))];
        setExpandedId((current) => current ?? merged[0]?.id ?? null);
        return merged;
      });
    }
  }, [historyData]);

  // Hide the shown answer when clicking anywhere that isn't the answer card or
  // the recent-questions list (e.g. empty panel space to the side or below).
  useEffect(() => {
    function handlePointerDown(e: MouseEvent) {
      const target = e.target as HTMLElement;
      if (!target.closest(".qa-interaction, .qa-recent")) {
        setExpandedId(null);
      }
    }
    document.addEventListener("mousedown", handlePointerDown);
    return () => document.removeEventListener("mousedown", handlePointerDown);
  }, []);

  const {
    mutate: askQuestion,
    isPending,
    error: askError,
    reset: resetAskError,
  } = $api.useMutation("post", "/api/v1/qa/ask");

  const { mutate: clearHistory, isPending: isClearing } = $api.useMutation(
    "delete",
    "/api/v1/qa/history",
    {
      onSuccess: () =>
        queryClient.invalidateQueries({
          queryKey: ["get", "/api/v1/qa/history"],
        }),
    },
  );

  function submitQuestion() {
    const trimmed = question.trim();
    if (!trimmed || isPending || historyLoading) return;

    resetAskError();
    askQuestion(
      { body: { question: trimmed } },
      {
        onSuccess(data) {
          setInteractions((prev) => [data, ...prev]);
          setExpandedId(data.id ?? null);
          setQuestion("");
        },
      },
    );
  }

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    submitQuestion();
  }

  function handleKeyDown(e: React.KeyboardEvent<HTMLTextAreaElement>) {
    if (e.key === "Enter" && (e.ctrlKey || e.metaKey)) {
      e.preventDefault();
      submitQuestion();
    }
  }

  // Only the explicitly selected interaction is shown; a null selection (e.g.
  // after clicking outside) hides the answer entirely.
  const activeInteraction =
    expandedId == null
      ? undefined
      : interactions.find((i) => (i.id ?? null) === expandedId);

  const askErrorMessage = askError
    ? errorMessage(askError, {
        notAuthenticated: "Authentication error. Retrying login...",
        forbidden: "You do not have permission to ask questions.",
        fallback: "Failed to get an answer. Please try again.",
      })
    : null;

  return (
    <section className="qa-panel">
      <form className="qa-form" onSubmit={handleSubmit}>
        <label htmlFor="qa-input" className="qa-form-label">
          Ask a question about your documents
        </label>
        <div className="qa-input-row">
          <textarea
            id="qa-input"
            className="qa-input"
            value={question}
            onChange={(e) => setQuestion(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder="e.g. What are the key findings in the Q3 report?"
            rows={3}
            disabled={isPending}
          />
          <div className="qa-submit-row">
            <p className="qa-input-hint">Press Ctrl+Enter to submit</p>
            <button
              type="submit"
              className="qa-submit"
              disabled={historyLoading || isPending || !question.trim()}
            >
              {isPending ? "Asking…" : "Ask"}
            </button>
          </div>
        </div>
      </form>

      {askErrorMessage && <p className="qa-error">{askErrorMessage}</p>}

      <div className="qa-answer-area">
        <div className="qa-current">
        {historyLoading && interactions.length === 0 && (
          <p className="qa-status">Loading history…</p>
        )}

        {historyError && !historyLoading && interactions.length === 0 && (
          <p className="qa-status qa-status--error">
            {errorMessage(historyError, {
              notAuthenticated: "Not authenticated.",
              forbidden: "You do not have permission to view Q&A history.",
              fallback: "Failed to load Q&A history.",
            })}
          </p>
        )}

        {!historyLoading && interactions.length === 0 && !historyError && (
          <p className="qa-empty">No questions yet. Ask something above.</p>
        )}

        {activeInteraction && (
          <article className="qa-interaction">
            <div className="qa-interaction__head">
              <p className="qa-question">{activeInteraction.question}</p>
              <button
                type="button"
                className="qa-hide"
                onClick={() => setExpandedId(null)}
              >
                Hide
              </button>
            </div>


            <div className="qa-answer">
              <ReactMarkdown>{activeInteraction.answer ?? ""}</ReactMarkdown>
            </div>

            {activeInteraction.citations &&
              activeInteraction.citations.length > 0 && (
                <section className="qa-sources">
                  <h4>Sources</h4>
                  <ul className="qa-source-list">
                    {activeInteraction.citations.map(
                      (citation, citationIndex) => {
                        const objectKey = citation.objectKey;
                        const doc = objectKey
                          ? findDocumentByKey(documents, objectKey)
                          : undefined;
                        const docId = citation.documentId ?? doc?.id;
                        const name =
                          citation.fileName ??
                          doc?.fileName ??
                          (objectKey
                            ? sourceKeyToName(objectKey)
                            : "Unknown source");
                        return (
                          <li
                            key={citation.marker ?? objectKey ?? citationIndex}
                            className="qa-source-item"
                          >
                            {docId ? (
                              <Link
                                to={`/documents/${docId}`}
                                className="qa-source-link"
                                title={citation.snippet ?? objectKey}
                              >
                                {name}
                              </Link>
                            ) : (
                              <span
                                className="qa-source-link qa-source-link--unresolved"
                                title={citation.snippet ?? objectKey}
                              >
                                {name}
                              </span>
                            )}
                          </li>
                        );
                      },
                    )}
                  </ul>
                </section>
              )}

            {(activeInteraction.timestamp || activeInteraction.modelUsed) && (
              <p className="qa-meta">
                {activeInteraction.timestamp && (
                  <span>{formatDateTime(activeInteraction.timestamp)}</span>
                )}
                {activeInteraction.timestamp && activeInteraction.modelUsed && (
                  <span className="qa-meta-sep"> · </span>
                )}
                {activeInteraction.modelUsed && (
                  <span>Model: {activeInteraction.modelUsed}</span>
                )}
              </p>
            )}
          </article>
        )}
      </div>

      {interactions.length > 0 && (
        <div className="qa-recent">
          <div className="qa-recent__head">
            <span className="qa-recent__label">Recent questions</span>
            <button
              type="button"
              className="qa-recent__clear"
              disabled={isClearing}
              onClick={() => {
                if (!confirm("Clear all Q&A history? This cannot be undone."))
                  return;
                clearHistory(
                  {},
                  {
                    onSuccess() {
                      setInteractions([]);
                      setExpandedId(null);
                    },
                  },
                );
              }}
            >
              {isClearing ? "Clearing…" : "Clear"}
            </button>
          </div>
          <ul className="qa-recent__list">
            {interactions.map((interaction, index) => {
              const key = interaction.id ?? index;
              const active = expandedId === key;
              return (
                <li key={key} className="qa-recent__item">
                  <button
                    type="button"
                    className={`qa-recent__query${active ? " qa-recent__query--active" : ""}`}
                    onClick={() => setExpandedId(key)}
                    title={interaction.question}
                  >
                    {interaction.question}
                  </button>
                  {interaction.timestamp && (
                    <span className="qa-recent__meta">
                      {formatDateTime(interaction.timestamp)}
                    </span>
                  )}
                </li>
              );
            })}
          </ul>
        </div>
      )}
      </div>
    </section>
  );
}
