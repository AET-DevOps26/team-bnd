import React, { useState } from "react";
import { Link, useNavigate, useParams } from "react-router";
import UploadDocument from "./UploadDocument";
import DotsLoader from "./DotsLoader";
import type { components } from "../api/schema";
import { formatDate } from "../utils/format";

type Document = components["schemas"]["Document"];
type TagDto = components["schemas"]["TagDto"];

const VISIBLE_TAG_COUNT = 5;

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
  allTags: TagDto[];
  selectedTags: string[];
  onToggleTag: (tagName: string) => void;
  onClearTags: () => void;
}

export default function DocumentTree({
  documents,
  isLoading,
  error,
  allTags,
  selectedTags,
  onToggleTag,
  onClearTags,
}: Props) {
  const navigate = useNavigate();
  const { id: selectedId } = useParams<{ id: string }>();
  const errorMessage =
    error instanceof Error ? error.message : error ? String(error) : "";

  const [tagsExpanded, setTagsExpanded] = useState(false);
  const hasHiddenTags = allTags.length > VISIBLE_TAG_COUNT;

  // If a selected tag is outside the visible set, force-expand so it's shown
  const visibleTagNames = allTags
    .slice(0, VISIBLE_TAG_COUNT)
    .map((t) => t.name ?? "");
  const hasSelectedHiddenTag = selectedTags.some(
    (t) => !visibleTagNames.includes(t),
  );
  const showAll = tagsExpanded || hasSelectedHiddenTag;
  const visibleTags = showAll ? allTags : allTags.slice(0, VISIBLE_TAG_COUNT);

  return (
    <nav className="document-tree" aria-label="Document list">
      <h2 className="tree-heading">Documents</h2>
      <UploadDocument onUploaded={(id) => void navigate(`/documents/${id}`)} />

      {/* Tag filter section */}
      <div className="tag-filter" aria-label="Filter by tags">
        <div className="tag-filter__header">
          <span className="tag-filter__label">Filter by tag</span>
          <button
            className="tag-filter__clear"
            onClick={onClearTags}
            type="button"
            style={{
              visibility: selectedTags.length > 0 ? "visible" : "hidden",
            }}
          >
            Clear
          </button>
        </div>
        {allTags.length === 0 ? (
          <p className="tag-filter__empty">
            Tag filtering becomes available once tags are generated or added.
          </p>
        ) : (
          <>
            <ul className="tag-filter__list">
              {visibleTags.map((tag) => {
                const name = tag.name ?? "";
                const active = selectedTags.includes(name);
                return (
                  <li key={name}>
                    <button
                      type="button"
                      className={`tag-filter__chip${active ? " tag-filter__chip--active" : ""}`}
                      onClick={() => onToggleTag(name)}
                      aria-pressed={active}
                    >
                      {name}
                      {tag.documentCount != null && (
                        <span className="tag-filter__count">
                          {tag.documentCount}
                        </span>
                      )}
                    </button>
                  </li>
                );
              })}
            </ul>
            {hasHiddenTags && !hasSelectedHiddenTag && (
              <button
                type="button"
                className="tag-filter__toggle"
                onClick={() => setTagsExpanded((v) => !v)}
              >
                {tagsExpanded
                  ? "Show less"
                  : `+${allTags.length - VISIBLE_TAG_COUNT} more`}
              </button>
            )}
          </>
        )}
      </div>

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
        <p className="tree-status">
          {selectedTags.length > 0
            ? "No documents match the selected tags."
            : "No documents yet."}
        </p>
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
                <Link
                  to={`/documents/${id}`}
                  className="tree-item__link"
                  aria-current={selectedId === id ? "true" : undefined}
                >
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
