package com.emailmanager.service;

import com.emailmanager.entity.ClassificationRule;
import com.emailmanager.entity.Email;
import com.emailmanager.repository.ClassificationRuleRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Rule-based email classification service
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RuleBasedClassificationService {

    private final ClassificationRuleRepository ruleRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Apply all active classification rules to an email
     */
    public void applyRules(Email email) {
        List<ClassificationRule> rules = ruleRepository.findByIsActiveTrueOrderByPriorityDesc();

        for (ClassificationRule rule : rules) {
            try {
                if (evaluateRule(rule, email)) {
                    applyActions(rule, email);
                    log.debug("Applied rule '{}' to email: {}", rule.getName(), email.getSubject());
                }
            } catch (Exception e) {
                log.error("Failed to apply rule: {}", rule.getName(), e);
            }
        }
    }

    /**
     * Evaluate if a rule matches an email
     */
    private boolean evaluateRule(ClassificationRule rule, Email email) {
        try {
            JsonNode conditions = objectMapper.readTree(rule.getConditions());

            boolean allMatch = true;
            boolean anyMatch = false;

            for (JsonNode condition : conditions) {
                boolean matches = evaluateCondition(condition, email);

                if (matches) {
                    anyMatch = true;
                } else {
                    allMatch = false;
                }
            }

            return rule.getMatchCondition() == ClassificationRule.MatchCondition.ALL ? allMatch : anyMatch;

        } catch (Exception e) {
            log.error("Failed to evaluate rule conditions", e);
            return false;
        }
    }

    /**
     * Evaluate a single condition
     */
    private boolean evaluateCondition(JsonNode condition, Email email) {
        String field = condition.get("field").asText();
        String operator = condition.get("operator").asText();
        String value = condition.get("value").asText().toLowerCase();

        String fieldValue = getFieldValue(field, email);
        if (fieldValue == null) {
            return false;
        }

        fieldValue = fieldValue.toLowerCase();

        return switch (operator) {
            case "contains" -> fieldValue.contains(value);
            case "equals" -> fieldValue.equals(value);
            case "starts_with" -> fieldValue.startsWith(value);
            case "ends_with" -> fieldValue.endsWith(value);
            case "not_contains" -> !fieldValue.contains(value);
            default -> false;
        };
    }

    /**
     * Get field value from email
     */
    private String getFieldValue(String field, Email email) {
        return switch (field) {
            case "subject" -> email.getSubject();
            case "from" -> email.getFromAddress();
            case "to" -> email.getToAddresses();
            case "body" -> email.getBodyPlainText();
            default -> null;
        };
    }

    /**
     * Apply rule actions to an email
     */
    private void applyActions(ClassificationRule rule, Email email) {
        try {
            JsonNode actions = objectMapper.readTree(rule.getActions());

            for (JsonNode action : actions) {
                String actionType = action.get("type").asText();
                String actionValue = action.has("value") ? action.get("value").asText() : null;

                switch (actionType) {
                    case "set_importance":
                        email.setImportance(Email.ImportanceLevel.valueOf(actionValue));
                        email.setClassificationReason("Rule: " + rule.getName());
                        break;

                    case "set_category":
                        email.setCategory(Email.EmailCategory.valueOf(actionValue));
                        break;

                    case "mark_spam":
                        email.setIsSpam(true);
                        email.setSpamScore(1.0);
                        break;

                    case "mark_phishing":
                        email.setIsPhishing(true);
                        email.setPhishingScore(1.0);
                        break;

                    default:
                        log.warn("Unknown action type: {}", actionType);
                }
            }
        } catch (Exception e) {
            log.error("Failed to apply rule actions", e);
        }
    }
}
