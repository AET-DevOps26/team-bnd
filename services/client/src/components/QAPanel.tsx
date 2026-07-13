import React, { useEffect, useRef, useState } from "react";
import ReactMarkdown from "react-markdown";
import $api from "../api/client";
import type { components } from "../api/schema";
import { useQueryClient } from "@tanstack/react-query";
import { formatDateTime } from "../utils/format";

type QAInteraction = components["schemas"]["QAInteraction"];
type Document = components["schemas"]["Document"];

interface Props {
  documents: Document[];
  onSelectDocument: (documentId: string) => void;
}

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
 * The objectKey stored on the Document entity is the S3 key used in sourceObjectKeys.
 */
function findDocumentByKey(
  documents: Document[],
  key: string,
): Document | undefined {
  return documents.find((doc) => doc.objectKey === key);
}

export default function QAPanel({ documents, onSelectDocument }: Props) {
  const queryClient = useQueryClient();
  const [question, setQuestion] = useState("");
  const [interactions, setInteractions] = useState<QAInteraction[]>([]);
  const historyLoaded = useRef(false);

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
        return [...prev, ...historyData.filter((i) => !seen.has(i.id))];
      });
    }
  }, [historyData]);

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
          <button
            type="submit"
            className="qa-submit"
            disabled={historyLoading || isPending || !question.trim()}
          >
            {isPending ? "Asking…" : "Ask"}
          </button>
        </div>
        <p className="qa-input-hint">Press Ctrl+Enter to submit</p>
      </form>

      {askErrorMessage && <p className="qa-error">{askErrorMessage}</p>}

      <div className="qa-history">
        {historyLoading && <p className="qa-status">Loading history…</p>}

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

        {interactions.map((interaction, index) => (
          <article key={interaction.id ?? index} className="qa-interaction">
            <p className="qa-question">{interaction.question}</p>

            <div className="qa-answer">
              <ReactMarkdown>{interaction.answer ?? ""}</ReactMarkdown>
            </div>

            {interaction.sourceObjectKeys &&
              interaction.sourceObjectKeys.length > 0 && (
                <section className="qa-sources">
                  <h4>Sources</h4>
                  <ul className="qa-source-list">
                    {interaction.sourceObjectKeys.map((key) => {
                      const doc = findDocumentByKey(documents, key);
                      const docId = doc?.id;
                      return (
                        <li key={key} className="qa-source-item">
                          {docId ? (
                            <button
                              type="button"
                              className="qa-source-link"
                              onClick={() => onSelectDocument(docId)}
                              title={key}
                            >
                              {doc?.fileName ?? sourceKeyToName(key)}
                            </button>
                          ) : (
                            <span
                              className="qa-source-link qa-source-link--unresolved"
                              title={key}
                            >
                              {sourceKeyToName(key)}
                            </span>
                          )}
                        </li>
                      );
                    })}
                  </ul>
                </section>
              )}

            {(interaction.timestamp || interaction.modelUsed) && (
              <p className="qa-meta">
                {interaction.timestamp && (
                  <span>{formatDateTime(interaction.timestamp)}</span>
                )}
                {interaction.timestamp && interaction.modelUsed && (
                  <span className="qa-meta-sep"> · </span>
                )}
                {interaction.modelUsed && (
                  <span>Model: {interaction.modelUsed}</span>
                )}
              </p>
            )}
          </article>
        ))}

        {interactions.length > 0 && (
          <div className="qa-clear">
            <button
              type="button"
              className="qa-clear-button"
              disabled={isClearing}
              onClick={() => {
                if (!confirm("Clear all Q&A history? This cannot be undone."))
                  return;
                clearHistory(
                  {},
                  {
                    onSuccess() {
                      setInteractions([]);
                    },
                  },
                );
              }}
            >
              {isClearing ? "Clearing…" : "Clear history"}
            </button>
          </div>
        )}
      </div>
    </section>
  );
}
