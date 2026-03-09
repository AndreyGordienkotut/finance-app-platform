--liquibase formatted sql
--changeset hordiienko:20260309-add-version-to-account
ALTER TABLE account ADD COLUMN version BIGINT NOT NULL DEFAULT 0;