import React, { useEffect, useState } from "react";
import ReactMarkdown from "react-markdown";
import { useParams } from "react-router";
import $api from "../api/client";
import { formatBytes, formatDateTime } from "../utils/format";

const ENTITY_TYPE_LABELS: Record<string, string> = {
  PERSON: "Person",
  DATE: "Date",
  TOPIC: "Topic",
  ORGANIZATION: "Organization",
};

export default function DocumentDetail() {
  const { id: documentId = null } = useParams<{ id: string }>();
  const {
    data: document,
    isLoading,
    error,
  } = $api.useQuery(
    "get",
    "/api/v1/knowledgebase/documents/{id}",
    { params: { path: { id: documentId ?? "" } } },
    { enabled: !!documentId },
  );
  const fileType = (document?.fileType ?? "").split(";")[0];
  const isPdf = fileType === "application/pdf";
  const isMarkdown = fileType === "text/markdown";
  const isPlainText = fileType === "text/plain";
  const [pdfUrl, setPdfUrl] = useState<string | null>(null);

  const {
    data: pdfData,
    isLoading: pdfLoading,
    isError: pdfError,
  } = $api.useQuery(
    "get",
    "/api/v1/knowledgebase/documents/{id}/download",
    {
      params: { path: { id: documentId ?? "" } },
      parseAs: "blob",
    },
    { enabled: !!documentId && isPdf },
  );

  const {
    data: textData,
    isLoading: textLoading,
    isError: textError,
  } = $api.useQuery(
    "get",
    "/api/v1/knowledgebase/documents/{id}/download",
    {
      params: { path: { id: documentId ?? "" } },
      parseAs: "text",
    },
    { enabled: !!documentId && (isMarkdown || isPlainText) },
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
            ? "Authentication error. Retrying login..."
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
          <dd>{formatBytes(document.fileSize, "\u2014")}</dd>
          <dt>Uploaded</dt>
          <dd>{formatDateTime(document.createdAt, "—")}</dd>
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

      {entities.length > 0 && (
        <section className="detail-section">
          <h3>Extracted Entities</h3>
          <ul className="entity-list" aria-label="Extracted entities">
            {entities.map((entity) => (
              <li
                key={entity.id}
                className={`entity-item entity-item--${entity.type?.toLowerCase() ?? "unknown"}`}
              >
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

      {summary && (
        <section className="detail-section">
          <h3>Summary</h3>
          <p className="detail-summary">{summary.content}</p>
          {summary.modelUsed && (
            <p className="detail-meta-small">Model: {summary.modelUsed}</p>
          )}
        </section>
      )}

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
          {textLoading && <p className="markdown-status">Loading preview…</p>}
          {textError && (
            <p className="markdown-status markdown-status--error">
              Failed to load markdown preview.
            </p>
          )}
          {textData != null && (
            <div className="markdown-body">
              <ReactMarkdown>{textData}</ReactMarkdown>
            </div>
          )}
        </section>
      )}

      {isPlainText && (
        <section className="detail-section">
          <h3>Document Preview</h3>
          {textLoading && <p className="plaintext-status">Loading preview…</p>}
          {textError && (
            <p className="plaintext-status plaintext-status--error">
              Failed to load preview.
            </p>
          )}
          {textData != null && <pre className="plaintext-body">{textData}</pre>}
        </section>
      )}
    </article>
  );
}
