-- Flyway V1 for qa-service.
--
-- Handles two cases idempotently:
--   1. Fresh install: creates qa_service and its tables from scratch.
--   2. Upgrade from the pre-split monolith: rewrites qa_interactions.user_id
--      (UUID FK) into qa_interactions.user_subject (OIDC subject string) by
--      joining against the users table, then moves qa_interactions and
--      qa_source_documents into qa_service.
--
-- users may live in public.users (monolith / user-service not yet migrated)
-- or user_service.users (user-service already migrated). Whichever exists
-- gets used.

CREATE SCHEMA IF NOT EXISTS qa_service;

DO $$
DECLARE
    users_table regclass;
BEGIN
    IF to_regclass('public.qa_interactions') IS NULL THEN
        RETURN;
    END IF;

    users_table := COALESCE(
        to_regclass('user_service.users'),
        to_regclass('public.users')
    );
    IF users_table IS NULL THEN
        RAISE EXCEPTION
          'Cannot migrate public.qa_interactions: neither user_service.users nor public.users exists';
    END IF;

    ALTER TABLE public.qa_interactions
        ADD COLUMN IF NOT EXISTS user_subject VARCHAR(255);
    EXECUTE format(
        'UPDATE public.qa_interactions q
            SET user_subject = u.oidc_subject
            FROM %s u
           WHERE q.user_subject IS NULL
             AND q.user_id = u.id',
        users_table
    );
    ALTER TABLE public.qa_interactions
        ALTER COLUMN user_subject SET NOT NULL;
    ALTER TABLE public.qa_interactions
        DROP COLUMN IF EXISTS user_id;

    ALTER TABLE public.qa_interactions SET SCHEMA qa_service;
    IF to_regclass('public.qa_source_documents') IS NOT NULL THEN
        ALTER TABLE public.qa_source_documents SET SCHEMA qa_service;
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS qa_service.qa_interactions (
    id            UUID         PRIMARY KEY,
    user_subject  VARCHAR(255) NOT NULL,
    question      TEXT         NOT NULL,
    answer        TEXT         NOT NULL,
    timestamp     TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    model_used    VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS qa_service.qa_source_documents (
    qa_id        UUID          NOT NULL,
    document_id  VARCHAR(255),
    CONSTRAINT fk_qa_source_documents_qa
        FOREIGN KEY (qa_id) REFERENCES qa_service.qa_interactions (id)
);
