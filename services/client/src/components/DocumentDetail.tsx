import React, { useEffect, useState } from "react";
import ReactMarkdown from "react-markdown";
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

  const isPdf = document?.fileType === "application/pdf";
  const isMarkdown = document?.fileType === "text/markdown";
  const [pdfUrl, setPdfUrl] = useState<string | null>(null);

  const {
    data: pdfData,
    isLoading: pdfLoading,
    isError: pdfError,
  } = $api.useQuery(
    "get",
    "/api/v1/knowledgebase/documents/{id}/download",
    {
      params: { path: { id: documentId! } },
      parseAs: "blob",
    },
    { enabled: !!documentId && isPdf },
  );

  const {
    data: markdownData,
    isLoading: markdownLoading,
    isError: markdownError,
  } = $api.useQuery(
    "get",
    "/api/v1/knowledgebase/documents/{id}/download",
    {
      params: { path: { id: documentId! } },
      parseAs: "text",
    },
    { enabled: !!documentId && isMarkdown },
  );

  useEffect(() => {
    if (!pdfData) {
      setPdfUrl(null);
      return;
    }

    const url = URL.createObjectURL(
      new Blob([pdfData as unknown as BlobPart], { type: "application/pdf" }),
    );
    setPdfUrl(url);

    return () => {
      URL.revokeObjectURL(url);
    };
  }, [pdfData]);

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
            : errorMessage === "FORBIDDEN"
              ? "You do not have permission to view this document."
              : "Failed to load document."}
        </p>
      </div>
    );
  }

  if (!document) return null;

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

      {isPdf && (
        <section className="detail-section">
          <h3>Document Preview</h3>
          {pdfLoading && <p className="pdf-embed-status">Loading preview…</p>}
          {pdfError && (
            <p className="pdf-embed-status pdf-embed-status--error">
              Failed to load PDF preview.
            </p>
          )}
          {pdfUrl && (
            <iframe
              className="pdf-embed"
              src={pdfUrl}
              title={`PDF preview of ${document.fileName ?? "document"}`}
            />
          )}
        </section>
      )}

      {isMarkdown && (
        <section className="detail-section">
          <h3>Document Preview</h3>
          {markdownLoading && (
            <p className="markdown-status">Loading preview…</p>
          )}
          {markdownError && (
            <p className="markdown-status markdown-status--error">
              Failed to load markdown preview.
            </p>
          )}
          {markdownData != null && (
            <div className="markdown-body">
              <ReactMarkdown>{markdownData}</ReactMarkdown>
            </div>
          )}
        </section>
      )}

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

    </article>
  );
}
