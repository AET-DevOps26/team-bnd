-- Flyway V1 for user-service.
--
-- This migration is idempotent and handles two cases:
--   1. Fresh install: no legacy public.users table exists. The script creates
--      the user_service schema and the users table from scratch.
--   2. Upgrade from the pre-split monolith: public.users exists (created by
--      the old app's ddl-auto=update). The script moves it into the
--      user_service schema without touching its rows.
--
-- The schema itself is also created by Flyway (spring.flyway.create-schemas),
-- but CREATE SCHEMA IF NOT EXISTS is repeated here so the script is safe to
-- run against a database that pre-dates Flyway.

CREATE SCHEMA IF NOT EXISTS user_service;

-- Upgrade path: move the legacy users table into the new schema. We only do
-- this if public.users still exists AND user_service.users does not, so this
-- is safe to re-run.
DO $$
BEGIN
    IF to_regclass('public.users') IS NOT NULL
       AND to_regclass('user_service.users') IS NULL THEN
        ALTER TABLE public.users SET SCHEMA user_service;
    END IF;
END $$;

-- Fresh install path: mirrors what Hibernate would have generated for the
-- User entity (see user-service/.../User.java). Kept in sync manually since
-- ddl-auto=validate will fail startup on drift.
CREATE TABLE IF NOT EXISTS user_service.users (
    id             UUID         PRIMARY KEY,
    oidc_subject   VARCHAR(255) NOT NULL,
    username       VARCHAR(255),
    email          VARCHAR(255),
    created_at     TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    preferences    VARCHAR(255),
    CONSTRAINT uk_users_oidc_subject UNIQUE (oidc_subject),
    CONSTRAINT uk_users_username     UNIQUE (username),
    CONSTRAINT uk_users_email        UNIQUE (email)
);
