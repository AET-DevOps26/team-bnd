import React from "react";
import $api from "../api/client";

interface Props {
  documentId: string | null;
}

const ENTITY_TYPE_LABELS: Record<string, string> = {
  PERSON: "Person",
  DATE: "Date",
  TOPIC: "Topic",
  ORGANIZATION: "Organization",
};

function formatDate(isoString?: string): string {
  if (!isoString) return "—";
  return new Date(isoString).toLocaleString(undefined, {
    year: "numeric",
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

function formatBytes(bytes?: number): string {
  if (bytes == null) return "—";
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

export default function DocumentDetail({ documentId }: Props) {
  const {
    data: document,
    isLoading,
    error,
  } = $api.useQuery(
    "get",
    "/api/v1/knowledgebase/documents/{id}",
    { params: { path: { id: documentId! } } },
    { enabled: !!documentId },
  );

  if (!documentId) {
    return (
      <div className="document-detail document-detail--empty">
        <p>Select a document to view its contents.</p>
      </div>
    );
  }

  if (isLoading) {
    return (
      <div className="document-detail document-detail--loading">
        <p>Loading…</p>
      </div>
    );
  }

  if (error) {
    const errorMessage = error instanceof Error ? error.message : String(error);
    return (
      <div className="document-detail document-detail--error">
        <p>
          {errorMessage === "NOT_AUTHENTICATED"
            ? "Not authenticated."
            : "Failed to load document."}
        </p>
      </div>
    );
  }

  if (!document) return null;

  // The API may return rawTextContent even though the schema doesn't declare it
  const rawTextContent = (document as { rawTextContent?: string })
    .rawTextContent;
  const tags = document.tags ?? [];
  const entities = document.extractedEntities ?? [];
  const summary = document.summary;

  return (
    <article className="document-detail">
      <header className="detail-header">
        <h2 className="detail-title">{document.fileName}</h2>
        <dl className="detail-meta">
          <dt>Type</dt>
          <dd>{document.fileType || "—"}</dd>
          <dt>Size</dt>
          <dd>{formatBytes(document.fileSize)}</dd>
          <dt>Uploaded</dt>
          <dd>{formatDate(document.createdAt)}</dd>
        </dl>
      </header>

      {tags.length > 0 && (
        <section className="detail-section">
          <h3>Tags</h3>
          <ul className="tag-list" aria-label="Document tags">
            {tags.map((tag) => (
              <li
                key={tag.id}
                className={`tag tag--${tag.source?.toLowerCase() ?? "user"}`}
              >
                {tag.label}
              </li>
            ))}
          </ul>
        </section>
      )}

      {summary && (
        <section className="detail-section">
          <h3>Summary</h3>
          <p className="detail-summary">{summary.content}</p>
          {summary.modelUsed && (
            <p className="detail-meta-small">Model: {summary.modelUsed}</p>
          )}
        </section>
      )}

      {entities.length > 0 && (
        <section className="detail-section">
          <h3>Extracted Entities</h3>
          <ul className="entity-list" aria-label="Extracted entities">
            {entities.map((entity) => (
              <li key={entity.id} className="entity-item">
                <span className="entity-name">{entity.name}</span>
                <span className="entity-type">
                  {(entity.type && ENTITY_TYPE_LABELS[entity.type]) ??
                    entity.type}
                </span>
              </li>
            ))}
          </ul>
        </section>
      )}

      {rawTextContent && (
        <section className="detail-section">
          <h3>Full Text</h3>
          <pre className="detail-raw-text">{rawTextContent}</pre>
        </section>
      )}
    </article>
  );
}
