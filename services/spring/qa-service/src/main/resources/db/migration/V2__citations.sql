-- Flyway V2 for qa-service: replace qa_source_documents with qa_citations.
--
-- The old table only stored a flat list of object keys per interaction. The new
-- schema keeps a marker (source id assigned by the GenAI service), the object
-- key, and the resolved document id / file name / snippet, so the client can
-- render clickable references. Old rows are copied over on a best-effort basis
-- with document_id / file_name left NULL and an empty snippet; their marker is
-- inferred from insertion order.

CREATE TABLE IF NOT EXISTS qa_service.qa_citations (
    qa_id        UUID          NOT NULL,
    position     INTEGER       NOT NULL,
    marker       INTEGER       NOT NULL,
    object_key   VARCHAR(512)  NOT NULL,
    document_id  VARCHAR(255),
    file_name    VARCHAR(255),
    snippet      TEXT,
    PRIMARY KEY (qa_id, position),
    CONSTRAINT fk_qa_citations_qa
        FOREIGN KEY (qa_id) REFERENCES qa_service.qa_interactions (id)
);

DO $$
BEGIN
    IF to_regclass('qa_service.qa_source_documents') IS NOT NULL THEN
        INSERT INTO qa_service.qa_citations (qa_id, position, marker, object_key, document_id, file_name, snippet)
        SELECT
            qa_id,
            ROW_NUMBER() OVER (PARTITION BY qa_id ORDER BY document_id) - 1 AS position,
            ROW_NUMBER() OVER (PARTITION BY qa_id ORDER BY document_id) AS marker,
            document_id,
            NULL, NULL, NULL
        FROM qa_service.qa_source_documents
        WHERE document_id IS NOT NULL;
        DROP TABLE qa_service.qa_source_documents;
    END IF;
END $$;
