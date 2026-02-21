--liquibase formatted sql

--changeset hordiienko:20251126-1
CREATE TABLE users (
                       id BIGINT NOT NULL AUTO_INCREMENT,
                       email VARCHAR(200) NOT NULL,
                       password VARCHAR(100) NOT NULL,
                       username VARCHAR(100) NOT NULL,
                       role VARCHAR(20) NOT NULL,
                       verified TINYINT(1) NOT NULL DEFAULT 0,
                       PRIMARY KEY (id),
                       UNIQUE INDEX email_UNIQUE (email ASC)
) ENGINE=InnoDB;

--changeset hordiienko:20251126-2
CREATE TABLE refresh_token (
                               id BIGINT NOT NULL AUTO_INCREMENT,
                               users_id BIGINT NOT NULL,
                               token VARCHAR(255) NOT NULL,
                               expiry_date DATETIME NOT NULL,
                               PRIMARY KEY (id),
                               CONSTRAINT fk_refresh_token_user FOREIGN KEY (users_id)
                                   REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB;

--changeset hordiienko:20251126-3
CREATE TABLE email_verification_tokens (
                                           id BIGINT NOT NULL AUTO_INCREMENT,
                                           token VARCHAR(255) NOT NULL,
                                           users_id BIGINT NOT NULL,
                                           expires_at DATETIME NOT NULL,
                                           used TINYINT(1) NOT NULL DEFAULT 0,
                                           PRIMARY KEY (id),
                                           CONSTRAINT fk_email_token_user FOREIGN KEY (users_id)
                                               REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB;