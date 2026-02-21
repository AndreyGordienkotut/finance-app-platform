--liquibase formatted sql

--changeset hordiienko:20251227-1
CREATE TABLE account (
                         id BIGINT NOT NULL AUTO_INCREMENT,
                         user_id BIGINT NOT NULL,
                         currency VARCHAR(3) NOT NULL,
                         balance DECIMAL(19,4) NOT NULL,
                         status_account VARCHAR(20) NOT NULL,
                         create_at DATETIME NOT NULL,
                         PRIMARY KEY (id)
) ENGINE=InnoDB;

--changeset hordiienko:20251227-2
CREATE TABLE applied_transactions (
                                      id BIGINT NOT NULL AUTO_INCREMENT,
                                      transaction_id BIGINT NOT NULL,
                                      account_id BIGINT NOT NULL,
                                      amount DECIMAL(19,4) NOT NULL,
                                      created_at DATETIME NOT NULL,
                                      PRIMARY KEY (id),
                                      CONSTRAINT fk_applied_tx_account FOREIGN KEY (account_id)
                                          REFERENCES account (id) ON DELETE CASCADE
) ENGINE=InnoDB;