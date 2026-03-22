--liquibase formatted sql

--changeset hordiienko:20260321-1
DROP TABLE IF EXISTS email_verification_tokens;

--changeset hordiienko:20260321-2

CREATE TABLE telegram_verification (
    id BIGINT NOT NULL AUTO_INCREMENT,
    users_id BIGINT NOT NULL,
    code VARCHAR(6) NOT NULL,
    chat_id BIGINT NULL,
    expires_at DATETIME NOT NULL,
    used TINYINT(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE INDEX code_UNIQUE (code ASC),
    CONSTRAINT fk_telegram_verification_user FOREIGN KEY (users_id)
    REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB;
