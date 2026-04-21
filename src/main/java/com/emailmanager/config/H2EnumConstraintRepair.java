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