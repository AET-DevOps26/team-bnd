import React, { useState } from "react";
import { Link, useNavigate, useParams } from "react-router";
import { useQueryClient } from "@tanstack/react-query";
import UploadDocument from "./UploadDocument";
import DotsLoader from "./DotsLoader";
import $api from "../api/client";
import type { components } from "../api/schema";
import { formatDate } from "../utils/format";
import { isProcessing } from "../utils/documentStatus";

type Document = components["schemas"]["Document"];
type TagDto = components["schemas"]["TagDto"];

export interface EntityFilterItem {
  name: string;
  documentCount: number;
}

export interface EntityGroup {
  type: string;
  names: EntityFilterItem[];
}

const ENTITY_TYPE_LABELS: Record<string, string> = {
  PERSON: "People",
  ORGANIZATION: "Organizations",
  TOPIC: "Topics",
  DATE: "Dates",
};

const VISIBLE_TAG_COUNT = 5;

interface Props {
  documents: Document[] | undefined;
  isLoading: boolean;
  error: unknown;
  allTags: TagDto[];
  selectedTags: string[];
  entityGroups: EntityGroup[];
  selectedEntities: string[];
  onToggleTag: (tagName: string) => void;
  onToggleEntity: (name: string) => void;
  onClearTags: () => void;
}

export default function DocumentTree({
  documents,
  isLoading,
  error,
  allTags,
  selectedTags,
  entityGroups,
  selectedEntities,
  onToggleTag,
  onToggleEntity,
  onClearTags,
}: Props) {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { id: selectedId } = useParams<{ id: string }>();
  const errorMessage =
    error instanceof Error ? error.message : error ? String(error) : "";

  const deleteMutation = $api.useMutation(
    "delete",
    "/api/v1/knowledgebase/documents/{id}",
  );

  function handleDelete(e: React.MouseEvent, id: string, name?: string) {
    e.preventDefault();
    if (!confirm(`Delete "${name ?? "this document"}"? This cannot be undone.`))
      return;
    deleteMutation.mutate(
      { params: { path: { id } } },
      {
        onSuccess: () => {
          void queryClient.invalidateQueries({
            queryKey: ["get", "/api/v1/knowledgebase/documents"],
          });
          if (selectedId === id) void navigate("/documents");
        },
      },
    );
  }

  const [tagsExpanded, setTagsExpanded] = useState(false);
  const [tagFilterOpen, setTagFilterOpen] = useState(true);
  const [entityFilterOpen, setEntityFilterOpen] = useState(false);
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
          <button
            type="button"
            className="tag-filter__toggle-head"
            onClick={() => setTagFilterOpen((v) => !v)}
            aria-expanded={tagFilterOpen}
          >
            <span
              className={`tag-filter__caret${tagFilterOpen ? " tag-filter__caret--open" : ""}`}
              aria-hidden="true"
            />
            <span className="tag-filter__label">Filter by tag</span>
          </button>
          <button
            className="tag-filter__clear"
            onClick={onClearTags}
            type="button"
            style={{
              visibility:
                selectedTags.length > 0 || selectedEntities.length > 0
                  ? "visible"
                  : "hidden",
            }}
          >
            Clear
          </button>
        </div>
        {tagFilterOpen &&
          (allTags.length === 0 ? (
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
          ))}
      </div>

      {entityGroups.length > 0 && (
        <div className="tag-filter" aria-label="Filter by entity">
          <div className="tag-filter__header">
            <button
              type="button"
              className="tag-filter__toggle-head"
              onClick={() => setEntityFilterOpen((v) => !v)}
              aria-expanded={entityFilterOpen}
            >
              <span
                className={`tag-filter__caret${entityFilterOpen ? " tag-filter__caret--open" : ""}`}
                aria-hidden="true"
              />
              <span className="tag-filter__label">Filter by entity</span>
            </button>
          </div>
          {entityFilterOpen &&
            entityGroups.map((group) => (
              <div key={group.type} className="entity-filter__group">
                <span className="entity-filter__group-label">
                  {ENTITY_TYPE_LABELS[group.type] ?? group.type}
                </span>
                <ul className="tag-filter__list">
                  {group.names.map((item) => {
                    const active = selectedEntities.includes(item.name);
                    return (
                      <li key={item.name}>
                        <button
                          type="button"
                          className={`tag-filter__chip entity-chip entity-chip--${group.type.toLowerCase()}${active ? " tag-filter__chip--active" : ""}`}
                          onClick={() => onToggleEntity(item.name)}
                          aria-pressed={active}
                        >
                          {item.name}
                          <span className="tag-filter__count">
                            {item.documentCount}
                          </span>
                        </button>
                      </li>
                    );
                  })}
                </ul>
              </div>
            ))}
        </div>
      )}

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
          {selectedTags.length > 0 || selectedEntities.length > 0
            ? "No documents match the selected filters."
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
                <button
                  type="button"
                  className="tree-item__delete"
                  onClick={(e) => handleDelete(e, id, doc.fileName)}
                  disabled={deleteMutation.isPending}
                  aria-label={`Delete ${doc.fileName}`}
                  title="Delete document"
                >
                  <span className="icon-trash" aria-hidden="true" />
                </button>
              </li>
            );
          })}
        </ul>
      )}
    </nav>
  );
}
