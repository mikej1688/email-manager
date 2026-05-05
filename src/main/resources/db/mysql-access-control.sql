-- =============================================================
-- Email Manager: MySQL Role-Based Access Control (production)
-- For the H2 development equivalent, see h2-access-control.sql
-- =============================================================
-- Run this script as a MySQL superuser (e.g. root) AFTER the
-- application has started once so that Hibernate has created all
-- tables via ddl-auto=update.
--
-- Three principals are defined:
--
--   emailmanager_app      Used by Spring Boot (datasource URL).
--                         Full read/write on all tables.
--
--   emailmanager_dev      For developers and support staff.
--                         Metadata only — encrypted PII columns
--                         (subject, from_address, from_name,
--                          to_addresses, body_plain_text, body_html)
--                         and credential columns are excluded so
--                         recipients cannot decrypt stored emails.
--
--   emailmanager_auditor  Compliance / security reviewer.
--                         Read-only access to audit_logs only.
--
-- IMPORTANT: Change every password below before deploying.
-- =============================================================

-- Adjust to match your production database name if different.
USE emailmanager;

-- -------------------------------------------------------------
-- 1. Application service account
--    Update application.properties datasource credentials to match.
-- -------------------------------------------------------------
CREATE USER IF NOT EXISTS 'emailmanager_app'@'%'
    IDENTIFIED BY 'CHANGE_THIS_app_password';

GRANT SELECT, INSERT, UPDATE, DELETE
    ON emailmanager.*
    TO 'emailmanager_app'@'%';


-- -------------------------------------------------------------
-- 2. Developer account — email metadata only, no PII
--
--    Excluded from emails:
--      subject, from_address, from_name, to_addresses,
--      body_plain_text, body_html  (all AES-256-GCM encrypted)
--
--    Excluded from email_accounts:
--      access_token, refresh_token, token_expiry_date,
--      encrypted_password  (OAuth tokens and IMAP credentials)
-- -------------------------------------------------------------
CREATE USER IF NOT EXISTS 'emailmanager_dev'@'%'
    IDENTIFIED BY 'CHANGE_THIS_dev_password';

-- emails: metadata columns only
GRANT SELECT (
    id,
    account_id,
    message_id,
    received_date,
    is_read,
    is_starred,
    importance,
    importance_score,
    category,
    is_spam,
    spam_score,
    is_phishing,
    phishing_score,
    classification_reason,
    due_date,
    requires_action,
    user_notified,
    folder_id,
    gmail_label_ids,
    processed_at,
    created_at,
    updated_at
) ON emailmanager.emails TO 'emailmanager_dev'@'%';

-- email_accounts: connection metadata only (no credentials/tokens)
GRANT SELECT (
    id,
    provider,
    display_name,
    email_address,
    is_active,
    provider_settings,
    last_sync_time,
    initial_sync_complete,
    sync_error,
    created_at
) ON emailmanager.email_accounts TO 'emailmanager_dev'@'%';

-- Non-sensitive tables: full read access
GRANT SELECT ON emailmanager.audit_logs            TO 'emailmanager_dev'@'%';
GRANT SELECT ON emailmanager.email_folders         TO 'emailmanager_dev'@'%';
GRANT SELECT ON emailmanager.classification_rules  TO 'emailmanager_dev'@'%';
GRANT SELECT ON emailmanager.notifications         TO 'emailmanager_dev'@'%';


-- -------------------------------------------------------------
-- 3. Auditor account — audit trail read only
-- -------------------------------------------------------------
CREATE USER IF NOT EXISTS 'emailmanager_auditor'@'%'
    IDENTIFIED BY 'CHANGE_THIS_auditor_password';

GRANT SELECT ON emailmanager.audit_logs TO 'emailmanager_auditor'@'%';


FLUSH PRIVILEGES;


-- =============================================================
-- How to wire the app service account into Spring Boot
-- =============================================================
-- In application.properties (or your prod config / secret store):
--
--   spring.datasource.url=jdbc:mysql://HOST:3306/emailmanager
--   spring.datasource.username=emailmanager_app
--   spring.datasource.password=<app password above>
--   spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
--   spring.jpa.database-platform=org.hibernate.dialect.MySQLDialect
--
-- How to connect as the dev account (read-only diagnostics):
--   mysql -u emailmanager_dev -p emailmanager
--   (column-level grants mean SELECT * on emails will be denied;
--    SELECT id, received_date, category FROM emails works fine)
-- =============================================================
