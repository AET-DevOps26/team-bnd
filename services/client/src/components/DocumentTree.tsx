import React from "react";
import $api from "../api/client";
import UploadDocument from "./UploadDocument";

interface Props {
  selectedId: string | null;
  onSelect: (id: string) => void;
}

function formatDate(isoString?: string): string {
  if (!isoString) return "";
  return new Date(isoString).toLocaleDateString(undefined, {
    year: "numeric",
    month: "short",
    day: "numeric",
  });
}

export default function DocumentTree({ selectedId, onSelect }: Props) {
  const {
    data: documents,
    isLoading,
    error,
  } = $api.useQuery("get", "/api/v1/knowledgebase/documents");

  const queryError: unknown = error;
  const errorMessage =
    queryError instanceof Error
      ? queryError.message
      : queryError
        ? String(queryError)
        : "";

  return (
    <nav className="document-tree" aria-label="Document list">
      <h2 className="tree-heading">Documents</h2>
      <UploadDocument onUploaded={onSelect} />
      {isLoading && <p className="tree-status">Loading…</p>}
      {error && (
        <p
          className={`tree-status ${
            errorMessage === "NOT_AUTHENTICATED"
              ? "tree-status--warn"
              : "tree-status--error"
          }`}
        >
          {errorMessage === "NOT_AUTHENTICATED"
            ? "Authentication error. Retrying login..."
            : errorMessage === "FORBIDDEN"
              ? "You do not have permission to view documents."
              : "Failed to load documents."}
        </p>
      )}
      {!isLoading && !error && documents?.length === 0 && (
        <p className="tree-status">No documents yet.</p>
      )}
      {!isLoading && !error && documents && documents.length > 0 && (
        <ul className="tree-list">
          {documents.map((doc) => {
            const id = doc.id;
            if (!id) return null;
            return (
              <li
                key={id}
                className={`tree-item${selectedId === id ? " tree-item--active" : ""}`}
                onClick={() => onSelect(id)}
                role="button"
                tabIndex={0}
                onKeyDown={(e) => e.key === "Enter" && onSelect(id)}
                aria-current={selectedId === id ? "true" : undefined}
              >
                <span className="tree-item__name">{doc.fileName}</span>
                <span className="tree-item__date">
                  {formatDate(doc.createdAt)}
                </span>
              </li>
            );
          })}
        </ul>
      )}
    </nav>
  );
}
