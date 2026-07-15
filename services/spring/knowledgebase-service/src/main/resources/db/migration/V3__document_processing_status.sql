-- V3: track entity extraction and tagging pipeline state on the document itself.
-- Unlike summaries, entities and tags are collections with no single row to update,
-- so the status lives on the parent document.
-- Existing rows default to COMPLETED (they were processed before this migration).

ALTER TABLE knowledgebase_service.documents
    ADD COLUMN IF NOT EXISTS entities_status  VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
    ADD COLUMN IF NOT EXISTS tags_status      VARCHAR(20) NOT NULL DEFAULT 'COMPLETED';
