import React from "react";
import { Link, useNavigate, useParams } from "react-router";
import UploadDocument from "./UploadDocument";
import DotsLoader from "./DotsLoader";
import type { components } from "../api/schema";
import { formatDate } from "../utils/format";

type Document = components["schemas"]["Document"];

function isProcessing(doc: Document): boolean {
  return (
    doc.summary?.status === "PENDING" ||
    doc.entitiesStatus === "PENDING" ||
    doc.tagsStatus === "PENDING"
  );
}

interface Props {
  documents: Document[] | undefined;
  isLoading: boolean;
  error: unknown;
}

export default function DocumentTree({ documents, isLoading, error }: Props) {
  const navigate = useNavigate();
  const { id: selectedId } = useParams<{ id: string }>();
  const errorMessage =
    error instanceof Error ? error.message : error ? String(error) : "";

  return (
    <nav className="document-tree" aria-label="Document list">
      <h2 className="tree-heading">Documents</h2>
      <UploadDocument onUploaded={(id) => void navigate(`/documents/${id}`)} />
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
            const id = doc.id;
            if (!id) return null;
            return (
              <li
                key={id}
                className={`tree-item${selectedId === id ? " tree-item--active" : ""}`}
              >
                <Link to={`/documents/${id}`} className="tree-item__link" aria-current={selectedId === id ? "true" : undefined}>
                  <span className="tree-item__name">
                    {doc.fileName}
                    {isProcessing(doc) && (
                      <span className="tree-item__loader">
                        <DotsLoader />
                      </span>
                    )}
                  </span>
                  <span className="tree-item__date">
                    {formatDate(doc.createdAt)}
                  </span>
                </Link>
              </li>
            );
          })}
        </ul>
      )}
    </nav>
  );
}
