-- SmartPay database schema (MySQL 8)
-- Safe to run more than once: nothing is dropped, every statement uses IF NOT EXISTS.
-- Run:  mysql -u root -p < docs/sql/schema.sql
--
-- The application starts with spring.jpa.hibernate.ddl-auto=validate,
-- so these tables must exist before the first run.
-- All money is simulated. Amounts are DECIMAL(19,2), never floating point.

CREATE DATABASE IF NOT EXISTS smartpay_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

USE smartpay_db;

-- 1. users ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    name          VARCHAR(100) NOT NULL,
    email         VARCHAR(254) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role          ENUM('USER','ADMIN')    NOT NULL DEFAULT 'USER',
    status        ENUM('ACTIVE','FROZEN') NOT NULL DEFAULT 'ACTIVE',
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_users_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 2. wallets (one wallet per user) ---------------------------------------
CREATE TABLE IF NOT EXISTS wallets (
    id         BIGINT        NOT NULL AUTO_INCREMENT,
    user_id    BIGINT        NOT NULL,
    balance    DECIMAL(19,2) NOT NULL DEFAULT 0.00,
    currency   VARCHAR(3)    NOT NULL DEFAULT 'INR',
    version    BIGINT        NOT NULL DEFAULT 0,
    created_at TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_wallets_user_id (user_id),
    CONSTRAINT fk_wallets_user FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_wallets_balance_nonnegative CHECK (balance >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 3. transactions (transfers and deposits) --------------------------------
-- idempotency_key is UNIQUE: this is what stops a repeated request from moving money twice.
-- A deposit has no sender wallet, so sender_wallet_id is NULL for deposits.
CREATE TABLE IF NOT EXISTS transactions (
    id                 BIGINT        NOT NULL AUTO_INCREMENT,
    reference_id       VARCHAR(64)   NOT NULL,
    idempotency_key    VARCHAR(128)  NOT NULL,
    sender_wallet_id   BIGINT        DEFAULT NULL,
    receiver_wallet_id BIGINT        DEFAULT NULL,
    amount             DECIMAL(19,2) NOT NULL,
    type               ENUM('TRANSFER','DEPOSIT','WITHDRAWAL') NOT NULL,
    status             ENUM('PENDING','SUCCESS','FAILED')      NOT NULL DEFAULT 'PENDING',
    failure_reason     VARCHAR(500)  DEFAULT NULL,
    created_at         TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_transactions_reference_id (reference_id),
    UNIQUE KEY uq_transactions_idempotency_key (idempotency_key),
    KEY idx_transactions_sender_created (sender_wallet_id, created_at),
    KEY idx_transactions_receiver_created (receiver_wallet_id, created_at),
    CONSTRAINT fk_transactions_receiver_wallet FOREIGN KEY (receiver_wallet_id) REFERENCES wallets (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_transactions_sender_wallet FOREIGN KEY (sender_wallet_id) REFERENCES wallets (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_transactions_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_transactions_different_wallets CHECK (
        sender_wallet_id IS NULL OR receiver_wallet_id IS NULL OR sender_wallet_id <> receiver_wallet_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 4. audit_logs (admin freeze / unfreeze history) -------------------------
CREATE TABLE IF NOT EXISTS audit_logs (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    actor_user_id  BIGINT        NOT NULL,
    target_user_id BIGINT        DEFAULT NULL,
    action         VARCHAR(50)   NOT NULL,
    details        VARCHAR(1000) DEFAULT NULL,
    created_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_audit_logs_actor_created (actor_user_id, created_at),
    KEY idx_audit_logs_target_created (target_user_id, created_at),
    CONSTRAINT fk_audit_logs_actor FOREIGN KEY (actor_user_id) REFERENCES users (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_audit_logs_target FOREIGN KEY (target_user_id) REFERENCES users (id)
        ON DELETE SET NULL ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Making an admin (there is no admin signup endpoint on purpose):
-- 1. Register a normal user through POST /api/auth/register
-- 2. Then run:  UPDATE users SET role = 'ADMIN' WHERE email = 'your@email.com';
