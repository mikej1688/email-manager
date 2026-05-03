package com.emailmanager.config;

import com.emailmanager.entity.Email;
import com.emailmanager.entity.EmailAccount;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class H2EnumConstraintRepair {

    private final JdbcTemplate jdbcTemplate;

    @EventListener(ApplicationReadyEvent.class)
    public void repairStaleEnumConstraints() {
        if (!isH2Database()) {
            return;
        }

        normalizeDeprecatedProviders();
        clearSystemLabelFolderMisassignment();
        widenEncryptedEmailColumns();
        restoreGmailInboxEmailsMisclassifiedAsSpam();

        repairConstraint(
                "EMAILS",
                "CATEGORY",
                Arrays.stream(Email.EmailCategory.values()).map(Enum::name).toList(),
                "email_category_check");

        repairConstraint(
                "EMAIL_ACCOUNTS",
                "PROVIDER",
                Arrays.stream(EmailAccount.EmailProvider.values()).map(Enum::name).toList(),
                "email_account_provider_check");
    }

    /**
     * Emails saved before the resolveFolder fix had folder_id set to EmailFolder
     * rows for system labels (Inbox, Sent, Spam, etc.). The category-based query
     * (findByAccountAndCategoryAndFolderIsNull) requires folder_id IS NULL for
     * system labels, so those emails were invisible in every folder view.
     *
     * This migration clears folder_id on such emails and removes the stale
     * EmailFolder rows so that routing works correctly going forward.
     */
    private void clearSystemLabelFolderMisassignment() {
        try {
            String systemNames = String.join(",",
                    "'Inbox'", "'Sent'", "'Trash'", "'Spam'", "'Drafts'",
                    "'Important'", "'Starred'", "'Social'", "'Promotions'",
                    "'Updates'", "'Forums'");

            int updated = jdbcTemplate.update(
                    "UPDATE emails SET folder_id = NULL "
                            + "WHERE folder_id IN "
                            + "(SELECT id FROM email_folders WHERE name IN (" + systemNames + "))");

            int deleted = jdbcTemplate.update(
                    "DELETE FROM email_folders WHERE name IN (" + systemNames + ")");

            if (updated > 0 || deleted > 0) {
                log.info("Startup repair: cleared folder_id on {} email(s) mis-assigned to "
                        + "system-label folders; deleted {} stale EmailFolder row(s)",
                        updated, deleted);
            }
        } catch (Exception e) {
            log.warn("Failed to clear system-label folder mis-assignments", e);
        }
    }

    /**
     * Encrypted column values are Base64-encoded ciphertext and can be
     * significantly
     * longer than the original VARCHAR limits. Widen the affected columns to CLOB
     * on startup so that H2 can store them. This is a no-op if the columns are
     * already large enough or if the table does not yet exist.
     */
    private void widenEncryptedEmailColumns() {
        String[] columns = { "SUBJECT", "FROM_ADDRESS", "FROM_NAME", "TO_ADDRESSES", "CC_ADDRESSES", "BODY_PLAIN_TEXT", "BODY_HTML" };
        for (String col : columns) {
            try {
                jdbcTemplate.execute("ALTER TABLE emails ALTER COLUMN " + col + " CLOB");
                log.debug("Widened emails.{} to CLOB for encryption support", col);
            } catch (Exception e) {
                log.debug("Could not widen emails.{} (already correct type or table not present): {}", col,
                        e.getMessage());
            }
        }
    }

    /**
     * Gmail emails whose category was overridden to SPAM by the app's local heuristic
     * detector should be restored to the category that Gmail's own labels indicate.
     * We identify them by the gmailLabelIds column: if it does NOT contain ",SPAM,"
     * (i.e. Gmail never marked them as spam) but the stored category is SPAM or
     * isSpam=true, we reset them.
     *
     * Mapping rules mirror GmailService.mapLabelsToCategory:
     *   TRASH → TRASH, DRAFT → DRAFT, SENT → SENT,
     *   CATEGORY_* tabs → their enum value, everything else → INBOX.
     */
    private void restoreGmailInboxEmailsMisclassifiedAsSpam() {
        try {
            // Emails that Gmail never flagged as spam but the app did
            int restored = jdbcTemplate.update(
                    "UPDATE emails SET category = CASE "
                    + "  WHEN gmail_label_ids LIKE '%,TRASH,%'              THEN 'TRASH' "
                    + "  WHEN gmail_label_ids LIKE '%,DRAFT,%'              THEN 'DRAFT' "
                    + "  WHEN gmail_label_ids LIKE '%,SENT,%'               THEN 'SENT' "
                    + "  WHEN gmail_label_ids LIKE '%,CATEGORY_SOCIAL,%'    THEN 'SOCIAL' "
                    + "  WHEN gmail_label_ids LIKE '%,CATEGORY_PROMOTIONS,%' THEN 'PROMOTIONS' "
                    + "  WHEN gmail_label_ids LIKE '%,CATEGORY_UPDATES,%'   THEN 'UPDATES' "
                    + "  WHEN gmail_label_ids LIKE '%,CATEGORY_FORUMS,%'    THEN 'FORUMS' "
                    + "  ELSE 'INBOX' "
                    + "END, "
                    + "is_spam = FALSE, is_phishing = FALSE, spam_score = NULL, phishing_score = NULL "
                    + "WHERE gmail_label_ids IS NOT NULL "
                    + "  AND gmail_label_ids NOT LIKE '%,SPAM,%' "
                    + "  AND (category = 'SPAM' OR is_spam = TRUE OR is_phishing = TRUE)");
            if (restored > 0) {
                log.info("Startup repair: restored {} Gmail email(s) incorrectly classified as spam back to their Gmail category",
                        restored);
            }
        } catch (Exception e) {
            log.warn("Failed to restore Gmail emails mis-classified as spam", e);
        }
    }

    private void normalizeDeprecatedProviders() {
        try {
            int updated = jdbcTemplate.update(
                    "UPDATE email_accounts SET provider = 'IMAP_GENERIC' WHERE provider = 'FACEBOOK'");
            if (updated > 0) {
                log.info("Normalized {} deprecated FACEBOOK account provider values to IMAP_GENERIC", updated);
            }
        } catch (Exception e) {
            log.warn("Failed to normalize deprecated email account provider values", e);
        }
    }

    private void repairConstraint(String tableName, String columnName, List<String> enumValues,
            String newConstraintName) {
        try {
            List<Map<String, Object>> constraints = jdbcTemplate.queryForList(
                    "SELECT tc.constraint_name, cc.check_clause "
                            + "FROM information_schema.table_constraints tc "
                            + "JOIN information_schema.check_constraints cc "
                            + "ON tc.constraint_catalog = cc.constraint_catalog "
                            + "AND tc.constraint_schema = cc.constraint_schema "
                            + "AND tc.constraint_name = cc.constraint_name "
                            + "WHERE tc.table_name = ? AND tc.constraint_type = 'CHECK'",
                    tableName);

            List<Map<String, Object>> matchingConstraints = constraints.stream()
                    .filter(row -> containsColumn(row, columnName))
                    .toList();

            if (matchingConstraints.isEmpty() || constraintAlreadyMatches(matchingConstraints, enumValues)) {
                return;
            }

            for (Map<String, Object> row : matchingConstraints) {
                String constraintName = String.valueOf(row.get("CONSTRAINT_NAME"));
                jdbcTemplate.execute(
                        "ALTER TABLE " + tableName.toLowerCase(Locale.ROOT) + " DROP CONSTRAINT " + constraintName);
                log.info("Dropped stale {} constraint {}", tableName, constraintName);
            }

            String allowedValues = enumValues.stream()
                    .map(value -> "'" + value + "'")
                    .collect(Collectors.joining(", "));
            jdbcTemplate.execute("ALTER TABLE " + tableName.toLowerCase(Locale.ROOT)
                    + " ADD CONSTRAINT " + newConstraintName
                    + " CHECK (" + columnName.toLowerCase(Locale.ROOT) + " IN (" + allowedValues + "))");
            log.info("Recreated {} constraint {} with values {}", tableName, newConstraintName, allowedValues);
        } catch (Exception e) {
            log.warn("Failed to repair enum constraint for {}.{}", tableName, columnName, e);
        }
    }

    private boolean constraintAlreadyMatches(List<Map<String, Object>> constraints, List<String> enumValues) {
        String valuesPattern = enumValues.stream()
                .map(value -> "'" + value + "'")
                .collect(Collectors.joining(","));

        return constraints.stream()
                .map(row -> String.valueOf(row.get("CHECK_CLAUSE")).toUpperCase(Locale.ROOT).replace(" ", ""))
                .anyMatch(clause -> clause.contains(valuesPattern));
    }

    private boolean containsColumn(Map<String, Object> row, String columnName) {
        Object clauseValue = row.get("CHECK_CLAUSE");
        if (clauseValue == null) {
            return false;
        }
        String clause = String.valueOf(clauseValue).toUpperCase(Locale.ROOT);
        return clause.contains(columnName);
    }

    private boolean isH2Database() {
        try {
            jdbcTemplate.queryForObject("SELECT H2VERSION()", String.class);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }
}