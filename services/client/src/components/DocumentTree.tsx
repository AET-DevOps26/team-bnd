import React from "react";
import UploadDocument from "./UploadDocument";
import type { components } from "../api/schema";
import { formatDate } from "../utils/format";

type Document = components["schemas"]["Document"];

interface Props {
  selectedId: string | null;
  onSelect: (id: string) => void;
  documents: Document[] | undefined;
  isLoading: boolean;
  error: unknown;
}

export default function DocumentTree({
  selectedId,
  onSelect,
  documents,
  isLoading,
  error,
}: Props) {
  const errorMessage =
    error instanceof Error ? error.message : error ? String(error) : "";

  return (
    <nav className="document-tree" aria-label="Document list">
      <h2 className="tree-heading">Documents</h2>
      <UploadDocument onUploaded={onSelect} />
      {isLoading && <p className="tree-status">Loading…</p>}
      {!!error && (
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
            if (!doc.id) return null;
            return (
              <li
                key={doc.id}
                className={`tree-item${selectedId === doc.id ? " tree-item--active" : ""}`}
                onClick={() => onSelect(doc.id!)}
                role="button"
                tabIndex={0}
                onKeyDown={(e) => e.key === "Enter" && onSelect(doc.id!)}
                aria-current={selectedId === doc.id ? "true" : undefined}
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
