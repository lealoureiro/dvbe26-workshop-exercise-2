-- Creates the Greek gods catalogue.
--
-- Identity (design D1): `id` is minted by the database. GENERATED ALWAYS, rather
-- than BY DEFAULT, means no caller can supply or override an identifier, so the
-- row really is the only place identity exists. An identifier is never a function
-- of content, which is what keeps a corrected spelling upstream from silently
-- changing a published identifier.
--
-- Natural key (design D1): `name` is unique case-insensitively, expressed as a
-- unique index on lower(name). That makes the natural key structural rather than
-- a matter of discipline, and it gives `greek-gods-upstream-sync` an arbiter for
-- a plain conditional insert (ON CONFLICT (lower(name)) DO NOTHING).
--
-- Dialect: PostgreSQL. Identity columns and expression indexes are both standard
-- here; no extension is required.
--
-- This migration creates structure only. It ships no writer and no seed data, so
-- the table is necessarily empty until reconciliation exists (design D2).

CREATE TABLE greek_god (
    id   BIGINT       GENERATED ALWAYS AS IDENTITY,
    name VARCHAR(100) NOT NULL,
    CONSTRAINT pk_greek_god PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uq_greek_god_name_lower
    ON greek_god (lower(name));
