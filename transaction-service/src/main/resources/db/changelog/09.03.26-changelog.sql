--liquibase formatted sql

--changeset hordiienko:20260309-add-version-to-transaction
ALTER TABLE transaction ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
-- changeset hordiienko:20260309-add-version-to-transaction_limit
ALTER TABLE transaction_limit ADD COLUMN version BIGINT NOT NULL DEFAULT 0;