-- MySQL schema for Email Manager (production profile)
-- All statements use CREATE TABLE IF NOT EXISTS so they are safe to re-run.
-- Hibernate ddl-auto=update will add any new columns introduced after initial creation.
--
-- Encrypted columns (subject, from_address, from_name, to_addresses, cc_addresses,
-- body_plain_text, body_html) use LONGTEXT because AES-256-GCM Base64 ciphertext
-- can be significantly larger than the original value.
-- Plain large-text columns (gmail_label_ids) also use LONGTEXT for safety.

SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

-- ============================================================
-- users  (must exist before email_accounts references it)
-- ============================================================
CREATE TABLE IF NOT EXISTS users (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    email       VARCHAR(255) NOT NULL,
    name        VARCHAR(255) NOT NULL,
    google_id   VARCHAR(255),
    role        VARCHAR(20)  NOT NULL DEFAULT 'USER',
    created_at  DATETIME,
    updated_at  DATETIME,
    PRIMARY KEY (id),
    UNIQUE KEY uq_users_email    (email),
    UNIQUE KEY uq_users_google_id (google_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- email_accounts
-- ============================================================
CREATE TABLE IF NOT EXISTS email_accounts (
    id                        BIGINT          NOT NULL AUTO_INCREMENT,
    email_address             VARCHAR(255)    NOT NULL,
    provider                  VARCHAR(50)     NOT NULL,
    display_name              VARCHAR(255)    NOT NULL,
    access_token              TEXT,
    refresh_token             TEXT,
    token_expiry_date         DATETIME,
    encrypted_password        VARCHAR(500),
    imap_server               VARCHAR(255),
    imap_port                 INT,
    smtp_server               VARCHAR(255),
    smtp_port                 INT,
    owner_id                  BIGINT,
    is_active                 BOOLEAN         NOT NULL DEFAULT TRUE,
    last_sync_time            DATETIME,
    initial_sync_complete     BOOLEAN         NOT NULL DEFAULT FALSE,
    gmail_background_page_token VARCHAR(500),
    created_at                DATETIME,
    updated_at                DATETIME,
    PRIMARY KEY (id),
    UNIQUE KEY uq_email_accounts_address (email_address),
    CONSTRAINT fk_accounts_owner
        FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- email_folders
-- ============================================================
CREATE TABLE IF NOT EXISTS email_folders (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    account_id       BIGINT       NOT NULL,
    name             VARCHAR(255) NOT NULL,
    description      VARCHAR(255),
    is_system_folder BOOLEAN      NOT NULL DEFAULT FALSE,
    folder_path      VARCHAR(255),
    gmail_label_id   VARCHAR(255),
    display_order    INT,
    created_at       DATETIME,
    updated_at       DATETIME,
    PRIMARY KEY (id),
    CONSTRAINT fk_folders_account
        FOREIGN KEY (account_id) REFERENCES email_accounts (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- emails
-- ============================================================
CREATE TABLE IF NOT EXISTS emails (
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    account_id            BIGINT       NOT NULL,
    message_id            VARCHAR(255) NOT NULL,
    -- Encrypted at rest via AttributeEncryptor (AES-256-GCM + Base64) — must be LONGTEXT
    subject               LONGTEXT     NOT NULL,
    from_address          LONGTEXT     NOT NULL,
    from_name             LONGTEXT,
    to_addresses          LONGTEXT,
    cc_addresses          LONGTEXT,
    body_plain_text       LONGTEXT,
    body_html             LONGTEXT,
    -- End encrypted columns
    received_date         DATETIME,
    is_read               BOOLEAN      NOT NULL DEFAULT FALSE,
    is_starred            BOOLEAN      NOT NULL DEFAULT FALSE,
    importance            VARCHAR(20)  NOT NULL DEFAULT 'NORMAL',
    category              VARCHAR(20)  NOT NULL DEFAULT 'INBOX',
    is_spam               BOOLEAN      NOT NULL DEFAULT FALSE,
    is_phishing           BOOLEAN      NOT NULL DEFAULT FALSE,
    importance_score      DOUBLE,
    spam_score            DOUBLE,
    phishing_score        DOUBLE,
    classification_reason VARCHAR(1000),
    due_date              DATETIME,
    requires_action       BOOLEAN      NOT NULL DEFAULT FALSE,
    user_notified         BOOLEAN      NOT NULL DEFAULT FALSE,
    folder_id             BIGINT,
    gmail_label_ids       LONGTEXT,
    processed_at          DATETIME,
    created_at            DATETIME,
    updated_at            DATETIME,
    PRIMARY KEY (id),
    UNIQUE KEY uq_emails_message_id (message_id),
    KEY idx_account_id   (account_id),
    KEY idx_received_date (received_date),
    KEY idx_is_read      (is_read),
    KEY idx_importance   (importance),
    CONSTRAINT fk_emails_account
        FOREIGN KEY (account_id) REFERENCES email_accounts (id) ON DELETE CASCADE,
    CONSTRAINT fk_emails_folder
        FOREIGN KEY (folder_id)  REFERENCES email_folders  (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- notifications
-- ============================================================
CREATE TABLE IF NOT EXISTS notifications (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    email_id   BIGINT,
    type       VARCHAR(50) NOT NULL,
    message    TEXT        NOT NULL,
    is_read    BOOLEAN     NOT NULL DEFAULT FALSE,
    sent_at    DATETIME,
    read_at    DATETIME,
    created_at DATETIME,
    PRIMARY KEY (id),
    CONSTRAINT fk_notifications_email
        FOREIGN KEY (email_id) REFERENCES emails (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- classification_rules
-- ============================================================
CREATE TABLE IF NOT EXISTS classification_rules (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    name            VARCHAR(255) NOT NULL,
    description     VARCHAR(255),
    rule_type       VARCHAR(50)  NOT NULL,
    match_condition VARCHAR(20)  NOT NULL DEFAULT 'ALL',
    conditions      TEXT,
    actions         TEXT,
    priority        INT,
    is_active       BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at      DATETIME,
    updated_at      DATETIME,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- recipient_addresses
-- ============================================================
CREATE TABLE IF NOT EXISTS recipient_addresses (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    email_address VARCHAR(320) NOT NULL,
    use_count     INT          NOT NULL DEFAULT 0,
    last_used_at  DATETIME     NOT NULL,
    created_at    DATETIME     NOT NULL,
    updated_at    DATETIME     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_recipient_address (email_address)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
