-- =============================================================
-- Email Manager: H2 Role-Based Access Control (development)
-- =============================================================
-- H2 does not support column-level GRANT, so restricted access
-- is implemented with views that expose only non-sensitive columns.
-- Developers connect as EMAILMANAGER_DEV and can only SELECT from
-- those views, not from the base tables directly.
--
-- HOW TO RUN
-- ----------
-- 1. Start the application so Hibernate creates all tables.
-- 2. Open http://localhost:8080/h2-console
--    JDBC URL : jdbc:h2:file:./data/emailmanager;AUTO_SERVER=TRUE
--    User     : sa
--    Password : (empty)
-- 3. Paste and run this entire script.
-- 4. Developers then connect with:
--    User     : EMAILMANAGER_DEV
--    Password : dev_password   (change below before sharing)
--
-- IMPORTANT: Change the passwords before sharing this script.
-- =============================================================

-- -------------------------------------------------------------
-- 1. Developer user — metadata only, no PII
-- -------------------------------------------------------------
CREATE USER IF NOT EXISTS EMAILMANAGER_DEV PASSWORD 'CHANGE_THIS_dev_password';

-- -------------------------------------------------------------
-- 2. Auditor user — audit_logs read only
-- -------------------------------------------------------------
CREATE USER IF NOT EXISTS EMAILMANAGER_AUDITOR PASSWORD 'CHANGE_THIS_auditor_password';

-- -------------------------------------------------------------
-- 3. Views — safe projections without encrypted PII
--
--    Excluded from emails:
--      subject, from_address, from_name, to_addresses,
--      body_plain_text, body_html   (AES-256-GCM encrypted)
--
--    Excluded from email_accounts:
--      access_token, refresh_token, token_expiry_date,
--      encrypted_password           (OAuth tokens / IMAP credentials)
-- -------------------------------------------------------------

CREATE VIEW IF NOT EXISTS emails_metadata AS
    SELECT
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
    FROM emails;

CREATE VIEW IF NOT EXISTS email_accounts_metadata AS
    SELECT
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
    FROM email_accounts;

-- -------------------------------------------------------------
-- 4. Grants for EMAILMANAGER_DEV
--    Views only — base tables are intentionally NOT granted.
-- -------------------------------------------------------------
GRANT SELECT ON emails_metadata          TO EMAILMANAGER_DEV;
GRANT SELECT ON email_accounts_metadata  TO EMAILMANAGER_DEV;
GRANT SELECT ON audit_logs               TO EMAILMANAGER_DEV;
GRANT SELECT ON email_folders            TO EMAILMANAGER_DEV;
GRANT SELECT ON classification_rules     TO EMAILMANAGER_DEV;
GRANT SELECT ON notifications            TO EMAILMANAGER_DEV;

-- -------------------------------------------------------------
-- 5. Grants for EMAILMANAGER_AUDITOR
-- -------------------------------------------------------------
GRANT SELECT ON audit_logs TO EMAILMANAGER_AUDITOR;

-- =============================================================
-- Verification queries (run as EMAILMANAGER_DEV to confirm):
--
--   SELECT * FROM emails_metadata LIMIT 5;          -- should work
--   SELECT body_html FROM emails LIMIT 1;           -- should fail
--   SELECT access_token FROM email_accounts LIMIT 1;-- should fail
--   SELECT * FROM audit_logs LIMIT 5;              -- should work
-- =============================================================
