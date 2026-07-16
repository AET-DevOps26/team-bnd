-- V2: track summary generation state so the client can distinguish
-- "still generating" from "failed" instead of both showing as absent.
--
-- Existing rows (created before this migration) already have content,
-- so they are COMPLETED. The column is nullable to allow inserting a
-- PENDING row before the LLM call, and content/model_used/generated_at
-- are relaxed to nullable accordingly.

ALTER TABLE knowledgebase_service.summaries
    ADD COLUMN IF NOT EXISTS status       VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
    ADD COLUMN IF NOT EXISTS error_message TEXT,
    ALTER COLUMN content      DROP NOT NULL,
    ALTER COLUMN model_used   DROP NOT NULL,
    ALTER COLUMN generated_at DROP NOT NULL;
