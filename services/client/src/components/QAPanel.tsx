import React, { useEffect, useRef, useState } from "react";
import ReactMarkdown from "react-markdown";
import $api from "../api/client";
import type { components } from "../api/schema";
import { useQueryClient } from "@tanstack/react-query";

type QAInteraction = components["schemas"]["QAInteraction"];
type Document = components["schemas"]["Document"];

interface Props {
  documents: Document[];
  onSelectDocument: (documentId: string) => void;
}

function formatDate(isoString?: string): string {
  if (!isoString) return "";
  return new Date(isoString).toLocaleString(undefined, {
    year: "numeric",
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
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

  // Seed interactions from history once on mount
  useEffect(() => {
    if (historyData && !historyLoaded.current) {
      historyLoaded.current = true;
      setInteractions(historyData);
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

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    const trimmed = question.trim();
    if (!trimmed || isPending) return;

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

  function handleKeyDown(e: React.KeyboardEvent<HTMLTextAreaElement>) {
    if (e.key === "Enter" && (e.ctrlKey || e.metaKey)) {
      e.preventDefault();
      handleSubmit(e as unknown as React.FormEvent);
    }
  }

  const askErrorMessage = (() => {
    if (!askError) return null;
    const err: unknown = askError;
    const msg = err instanceof Error ? err.message : String(err);
    if (msg === "NOT_AUTHENTICATED")
      return "Authentication error. Retrying login...";
    if (msg === "FORBIDDEN")
      return "You do not have permission to ask questions.";
    return "Failed to get an answer. Please try again.";
  })();

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
            {(() => {
              const err: unknown = historyError;
              const msg = err instanceof Error ? err.message : String(err);
              if (msg === "NOT_AUTHENTICATED") return "Not authenticated.";
              if (msg === "FORBIDDEN")
                return "You do not have permission to view Q&A history.";
              return "Failed to load Q&A history.";
            })()}
          </p>
        )}

        {!historyLoading && interactions.length === 0 && !historyError && (
          <p className="qa-empty">No questions yet. Ask something above.</p>
        )}

        {interactions.map((interaction) => (
          <article key={interaction.id} className="qa-interaction">
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
                      return (
                        <li key={key} className="qa-source-item">
                          {doc ? (
                            <button
                              type="button"
                              className="qa-source-link"
                              onClick={() => onSelectDocument(doc.id!)}
                              title={key}
                            >
                              {doc.fileName ?? sourceKeyToName(key)}
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
                  <span>{formatDate(interaction.timestamp)}</span>
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
                      historyLoaded.current = false;
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
