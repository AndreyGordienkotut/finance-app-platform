--liquibase formatted sql

--changeset hordiienko:20260221-1
CREATE TABLE transaction_category (
                                      id BIGINT NOT NULL AUTO_INCREMENT,
                                      user_id BIGINT NULL,
                                      name VARCHAR(100) NOT NULL,
                                      PRIMARY KEY (id)
) ENGINE=InnoDB;

--changeset hordiienko:20260221-2
CREATE TABLE transaction (
                             id BIGINT NOT NULL AUTO_INCREMENT,
                             user_id BIGINT NOT NULL,
                             source_account_id BIGINT NULL,
                             target_account_id BIGINT NULL,
                             amount DECIMAL(19,4) NOT NULL,
                             currency VARCHAR(3) NOT NULL,
                             status VARCHAR(20) NOT NULL,
                             create_at DATETIME NOT NULL,
                             update_at DATETIME NULL,
                             idempotency_key VARCHAR(150) NOT NULL,
                             transaction_type VARCHAR(45) NOT NULL,
                             transaction_step VARCHAR(20) NOT NULL,
                             exchange_rate DECIMAL(19,4) NULL,
                             target_amount DECIMAL(19,4) NULL,
                             transaction_category_id BIGINT NULL,
                             error_message TEXT NULL,
                             PRIMARY KEY (id),
                             UNIQUE INDEX idempotency_key_UNIQUE (idempotency_key ASC),
                             CONSTRAINT fk_tx_category FOREIGN KEY (transaction_category_id)
                                 REFERENCES transaction_category (id)
) ENGINE=InnoDB;

--changeset hordiienko:20260221-3
CREATE TABLE transaction_limit (
                                   id BIGINT NOT NULL AUTO_INCREMENT,
                                   user_id BIGINT NOT NULL,
                                   daily_limit DECIMAL(19,4) NOT NULL,
                                   single_limit DECIMAL(19,4) NOT NULL,
                                   PRIMARY KEY (id),
                                   UNIQUE INDEX user_id_UNIQUE (user_id ASC)
) ENGINE=InnoDB;