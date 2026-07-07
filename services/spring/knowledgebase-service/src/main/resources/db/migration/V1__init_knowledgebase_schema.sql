-- Flyway V1 for knowledgebase-service.
--
-- Handles two cases idempotently:
--   1. Fresh install: no legacy public.documents. Creates knowledgebase_service
--      and every table from scratch.
--   2. Upgrade from the pre-split monolith: public.documents exists with the
--      old owner_id UUID column. This script rewrites the ownership columns
--      (owner_id -> owner_subject on documents, user_id -> user_subject on
--      search_queries) by joining against the users table, then moves the
--      whole set of tables into knowledgebase_service.
--
-- The users table may live in either public.users (if user-service has not
-- run its own V1 yet) or user_service.users (if it has). We resolve
-- whichever exists and fall back gracefully. In a fresh install neither
-- exists AND public.documents does not exist, so the DO block is skipped.

CREATE SCHEMA IF NOT EXISTS knowledgebase_service;

DO $$
DECLARE
    users_table regclass;
BEGIN
    IF to_regclass('public.documents') IS NULL THEN
        RETURN;
    END IF;

    users_table := COALESCE(
        to_regclass('user_service.users'),
        to_regclass('public.users')
    );
    IF users_table IS NULL THEN
        RAISE EXCEPTION
          'Cannot migrate public.documents: neither user_service.users nor public.users exists';
    END IF;

    -- documents.owner_id (UUID FK) -> documents.owner_subject (OIDC subject string)
    ALTER TABLE public.documents
        ADD COLUMN IF NOT EXISTS owner_subject VARCHAR(255);
    EXECUTE format(
        'UPDATE public.documents d
            SET owner_subject = u.oidc_subject
            FROM %s u
           WHERE d.owner_subject IS NULL
             AND d.owner_id = u.id',
        users_table
    );
    ALTER TABLE public.documents
        ALTER COLUMN owner_subject SET NOT NULL;
    ALTER TABLE public.documents
        DROP COLUMN IF EXISTS owner_id;

    -- search_queries.user_id (UUID FK) -> search_queries.user_subject
    IF to_regclass('public.search_queries') IS NOT NULL THEN
        ALTER TABLE public.search_queries
            ADD COLUMN IF NOT EXISTS user_subject VARCHAR(255);
        EXECUTE format(
            'UPDATE public.search_queries s
                SET user_subject = u.oidc_subject
                FROM %s u
               WHERE s.user_subject IS NULL
                 AND s.user_id = u.id',
            users_table
        );
        ALTER TABLE public.search_queries
            ALTER COLUMN user_subject SET NOT NULL;
        ALTER TABLE public.search_queries
            DROP COLUMN IF EXISTS user_id;
    END IF;

    -- Move all knowledgebase tables into the new schema.
    ALTER TABLE public.documents SET SCHEMA knowledgebase_service;
    IF to_regclass('public.summaries')          IS NOT NULL THEN ALTER TABLE public.summaries          SET SCHEMA knowledgebase_service; END IF;
    IF to_regclass('public.tags')               IS NOT NULL THEN ALTER TABLE public.tags               SET SCHEMA knowledgebase_service; END IF;
    IF to_regclass('public.document_tags')      IS NOT NULL THEN ALTER TABLE public.document_tags      SET SCHEMA knowledgebase_service; END IF;
    IF to_regclass('public.extracted_entities') IS NOT NULL THEN ALTER TABLE public.extracted_entities SET SCHEMA knowledgebase_service; END IF;
    IF to_regclass('public.search_queries')     IS NOT NULL THEN ALTER TABLE public.search_queries     SET SCHEMA knowledgebase_service; END IF;
END $$;

-- Fresh-install schema. Column types mirror what Hibernate generates for the
-- knowledgebase entities; ddl-auto=validate will fail startup on any drift.
CREATE TABLE IF NOT EXISTS knowledgebase_service.documents (
    id                UUID          PRIMARY KEY,
    owner_subject     VARCHAR(255)  NOT NULL,
    file_name         VARCHAR(255)  NOT NULL,
    object_key        VARCHAR(255)  NOT NULL,
    file_type         VARCHAR(255)  NOT NULL,
    file_size         BIGINT        NOT NULL,
    raw_text_content  TEXT,
    created_at        TIMESTAMP(6) WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS knowledgebase_service.tags (
    id      UUID         PRIMARY KEY,
    label   VARCHAR(255) NOT NULL,
    source  VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS knowledgebase_service.summaries (
    id            UUID         PRIMARY KEY,
    document_id   UUID         NOT NULL,
    content       TEXT         NOT NULL,
    generated_at  TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    model_used    VARCHAR(255) NOT NULL,
    CONSTRAINT fk_summaries_document
        FOREIGN KEY (document_id) REFERENCES knowledgebase_service.documents (id)
);

CREATE TABLE IF NOT EXISTS knowledgebase_service.document_tags (
    document_id  UUID  NOT NULL,
    tag_id       UUID  NOT NULL,
    PRIMARY KEY (document_id, tag_id),
    CONSTRAINT fk_document_tags_document
        FOREIGN KEY (document_id) REFERENCES knowledgebase_service.documents (id),
    CONSTRAINT fk_document_tags_tag
        FOREIGN KEY (tag_id) REFERENCES knowledgebase_service.tags (id)
);

CREATE TABLE IF NOT EXISTS knowledgebase_service.extracted_entities (
    id           UUID             PRIMARY KEY,
    document_id  UUID             NOT NULL,
    name         VARCHAR(255)     NOT NULL,
    type         VARCHAR(255)     NOT NULL,
    confidence   DOUBLE PRECISION NOT NULL,
    CONSTRAINT fk_extracted_entities_document
        FOREIGN KEY (document_id) REFERENCES knowledgebase_service.documents (id)
);

CREATE TABLE IF NOT EXISTS knowledgebase_service.search_queries (
    id            UUID         PRIMARY KEY,
    user_subject  VARCHAR(255) NOT NULL,
    query_text    VARCHAR(255) NOT NULL,
    timestamp     TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    result_count  INTEGER      NOT NULL
);
