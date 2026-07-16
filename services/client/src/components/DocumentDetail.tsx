import React, { useEffect, useRef, useState } from "react";
import ReactMarkdown from "react-markdown";
import { useOutletContext, useParams } from "react-router";
import { useQueryClient } from "@tanstack/react-query";
import $api from "../api/client";
import { formatBytes, formatDateTime } from "../utils/format";
import { isProcessing, pollWhileProcessing } from "../utils/documentStatus";
import DotsLoader from "./DotsLoader";
import type { MainViewContext } from "./MainView";

const ENTITY_TYPE_LABELS: Record<string, string> = {
  PERSON: "Person",
  DATE: "Date",
  TOPIC: "Topic",
  ORGANIZATION: "Organization",
};
export default function DocumentDetail() {
  const { id: documentId = null } = useParams<{ id: string }>();
  const { onToggleTag } = useOutletContext<MainViewContext>();
  const queryClient = useQueryClient();
  const [newTagLabel, setNewTagLabel] = useState("");
  const [addingTag, setAddingTag] = useState(false);
  const tagInputRef = useRef<HTMLInputElement>(null);

  const {
    data: document,
    isLoading,
    error,
  } = $api.useQuery(
    "get",
    "/api/v1/knowledgebase/documents/{id}",
    { params: { path: { id: documentId ?? "" } } },
    {
      enabled: !!documentId,
      // Poll while any pipeline step is still in progress, with a cap so a stuck
      // document doesn't keep us polling forever.
      refetchInterval: (query) => {
        const doc = query.state.data;
        if (!doc) return false;
        return pollWhileProcessing(
          isProcessing(doc),
          query.state.dataUpdateCount,
        );
      },
    },
  );

  const addTagMutation = $api.useMutation(
    "post",
    "/api/v1/knowledgebase/documents/{id}/tags",
  );

  const removeTagMutation = $api.useMutation(
    "delete",
    "/api/v1/knowledgebase/documents/{documentId}/tags/{tagId}",
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

  function invalidateAfterTagChange() {
    // Refetch both the document detail and the documents list + tags list
    void queryClient.invalidateQueries({
      queryKey: ["get", "/api/v1/knowledgebase/documents/{id}"],
    });
    void queryClient.invalidateQueries({
      queryKey: ["get", "/api/v1/knowledgebase/documents"],
    });
    void queryClient.invalidateQueries({
      queryKey: ["get", "/api/v1/knowledgebase/tags"],
    });
  }

  function closeAddTag() {
    setNewTagLabel("");
    setAddingTag(false);
    addTagMutation.reset();
  }

  function handleAddTag() {
    const label = newTagLabel.trim();
    if (!label || !documentId) return;
    addTagMutation.mutate(
      {
        params: { path: { id: documentId } },
        body: { label },
      },
      {
        onSuccess: () => {
          closeAddTag();
          invalidateAfterTagChange();
        },
      },
    );
  }

  function handleTagInputKeyDown(e: React.KeyboardEvent) {
    if (e.key === "Enter") {
      e.preventDefault();
      handleAddTag();
    } else if (e.key === "Escape") {
      closeAddTag();
    }
  }

  function handleTagInputBlur() {
    if (newTagLabel.trim()) {
      handleAddTag();
    } else {
      closeAddTag();
    }
  }

  function handleRemoveTag(tagId: string) {
    if (!documentId) return;
    removeTagMutation.mutate(
      {
        params: { path: { documentId, tagId } },
      },
      {
        onSuccess: () => {
          invalidateAfterTagChange();
        },
      },
    );
  }

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

  // Sort tags alphabetically to keep a consistent display order (#301)
  const tags = [...(document.tags ?? [])].sort((a, b) =>
    (a.label ?? "").localeCompare(b.label ?? ""),
  );
  const entities = document.extractedEntities ?? [];
  const summary = document.summary;
  const entitiesStatus = document.entitiesStatus;
  const tagsStatus = document.tagsStatus;
  const hasPreview = isPdf || isMarkdown || isPlainText;

  const header = (
    <header className="detail-header">
      <h2 className="detail-title">{document.fileName}</h2>
    </header>
  );

  const infoSection = (
    <section className="detail-section">
      <h3>File info</h3>
      <dl className="detail-meta">
        <dt>Type</dt>
        <dd>{document.fileType || "—"}</dd>
        <dt>Size</dt>
        <dd>{formatBytes(document.fileSize, "\u2014")}</dd>
        <dt>Uploaded</dt>
        <dd>{formatDateTime(document.createdAt, "—")}</dd>
      </dl>
    </section>
  );

  const tagsSection = (
    <section className="detail-section">
      <h3>Tags</h3>
      {tagsStatus === "PENDING" && <DotsLoader />}
      {tagsStatus === "FAILED" && tags.length === 0 && (
        <p className="detail-tags-status detail-tags-status--error">
          Tag generation failed.
        </p>
      )}
      <ul className="tag-list" aria-label="Document tags">
        {tags.map((tag) => (
          <li
            key={tag.id}
            className={`tag tag--${tag.source?.toLowerCase() ?? "user"}`}
          >
            <button
              type="button"
              className="tag__label tag__label--clickable"
              onClick={() => onToggleTag(tag.label ?? "")}
              title={`Filter by "${tag.label}"`}
            >
              {tag.label}
            </button>
            {tag.source === "USER" && (
              <button
                type="button"
                className="tag__remove"
                onClick={() => handleRemoveTag(tag.id ?? "")}
                aria-label={`Remove tag ${tag.label}`}
                disabled={removeTagMutation.isPending}
              >
                &#215;
              </button>
            )}
          </li>
        ))}
        <li className="tag tag--add">
          {addingTag ? (
            <span className="tag__input-wrapper">
              <input
                ref={tagInputRef}
                type="text"
                className="tag__input"
                value={newTagLabel}
                onChange={(e) => setNewTagLabel(e.target.value)}
                onKeyDown={handleTagInputKeyDown}
                onBlur={handleTagInputBlur}
                placeholder="tag name"
                aria-label="New tag name"
                maxLength={50}
                autoFocus
              />
              <button
                type="button"
                className="tag__confirm"
                onMouseDown={(e) => e.preventDefault()}
                onClick={handleAddTag}
                aria-label="Confirm new tag"
                disabled={!newTagLabel.trim() || addTagMutation.isPending}
              >
                &#10003;
              </button>
            </span>
          ) : (
            <button
              type="button"
              className="tag__add-button"
              onClick={() => setAddingTag(true)}
              aria-label="Add a tag"
            >
              <span className="tag__add-icon" aria-hidden="true">
                +
              </span>
              Add tag
            </button>
          )}
        </li>
      </ul>
      {addingTag && addTagMutation.isError && (
        <p className="tag-add-error">Failed to add tag.</p>
      )}
    </section>
  );

  const entitiesSection = (entitiesStatus === "PENDING" ||
    entitiesStatus === "FAILED" ||
    entities.length > 0) && (
    <section className="detail-section">
      <h3>Extracted Entities</h3>
      {entitiesStatus === "PENDING" && <DotsLoader />}
      {entitiesStatus === "FAILED" && entities.length === 0 && (
        <p className="detail-entities-status detail-entities-status--error">
          Entity extraction failed.
        </p>
      )}
      {entities.length > 0 && (
        <ul className="entity-list" aria-label="Extracted entities">
          {entities.map((entity) => (
            <li
              key={entity.id}
              className={`entity-item entity-item--${entity.type?.toLowerCase() ?? "unknown"}`}
            >
              <span className="entity-name">{entity.name}</span>
              <span className="entity-type">
                {(entity.type && ENTITY_TYPE_LABELS[entity.type]) ?? entity.type}
              </span>
            </li>
          ))}
        </ul>
      )}
    </section>
  );

  const summarySection = summary && (
    <section className="detail-section">
      <h3>Summary</h3>
      {summary.status === "PENDING" && <DotsLoader />}
      {summary.status === "FAILED" && (
        <p className="detail-summary detail-summary--error">
          Summary generation failed. You can try again via reprocess.
        </p>
      )}
      {summary.status === "COMPLETED" && (
        <>
          <p className="detail-summary">{summary.content}</p>
          {summary.modelUsed && (
            <p className="detail-meta-small">Model: {summary.modelUsed}</p>
          )}
        </>
      )}
    </section>
  );

  const preview = (
    <>
      {isPdf && (
        <section className="detail-section detail-section--preview">
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
        <section className="detail-section detail-section--preview">
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
        <section className="detail-section detail-section--preview">
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
    </>
  );

  if (hasPreview) {
    return (
      <article className="document-detail document-detail--split">
        {header}
        <div className="detail-split">
          <div className="detail-preview">
            {summarySection}
            {preview}
          </div>
          <aside className="detail-aside">
            {infoSection}
            {tagsSection}
            {entitiesSection}
          </aside>
        </div>
      </article>
    );
  }

  return (
    <article className="document-detail">
      {header}
      {infoSection}
      {tagsSection}
      {entitiesSection}
      {summarySection}
    </article>
  );
}
