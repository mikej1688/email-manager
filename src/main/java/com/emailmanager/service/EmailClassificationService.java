package com.emailmanager.service;

import com.emailmanager.entity.Email;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Main service for email classification
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EmailClassificationService {

    private final RuleBasedClassificationService ruleBasedService;
    private final AIClassificationService aiClassificationService;
    private final SpamDetectionService spamDetectionService;
    private final DueDateExtractionService dueDateService;
    private final NotificationService notificationService;

    /**
     * Classify an email using hybrid approach (rules + AI)
     */
    public void classifyEmail(Email email) {
        log.debug("Classifying email: {}", email.getSubject());

        // Gmail emails already carry authoritative label data (SPAM, INBOX, etc.) set
        // by Google's own filter. Running a local heuristic on top produces too many
        // false positives and overrides the correct Gmail category, so we skip it.
        // For non-Gmail (IMAP) accounts there is no server-side classification, so
        // local spam/phishing detection is still applied there.
        boolean isGmailEmail = email.getGmailLabelIds() != null;

        if (!isGmailEmail) {
            spamDetectionService.detectSpamAndPhishing(email);

            if (email.getIsSpam() || email.getIsPhishing()) {
                log.info("Email classified as spam/phishing: {}", email.getSubject());
                if (email.getIsPhishing()) {
                    notificationService.sendPhishingAlert(email);
                }
                return;
            }
        }

        // 2. Apply rule-based classification
        ruleBasedService.applyRules(email);

        // 3. Apply AI classification for importance if needed
        if (email.getImportance() == Email.ImportanceLevel.NORMAL) {
            aiClassificationService.classifyImportance(email);
        }

        // 4. Extract due dates and deadlines
        dueDateService.extractDueDate(email);

        // 5. Determine if user action is required
        determineActionRequired(email);

        // 6. Send notifications if needed
        if (email.getImportance() == Email.ImportanceLevel.URGENT) {
            notificationService.sendUrgentEmailAlert(email);
        }

        if (email.getDueDate() != null) {
            notificationService.sendDeadlineNotification(email);
        }

        log.debug("Email classified - Importance: {}, Category: {}, Spam: {}",
                email.getImportance(), email.getCategory(), email.getIsSpam());
    }

    /**
     * Determine if email requires user action
     */
    private void determineActionRequired(Email email) {
        // Email requires action if:
        // 1. It's marked as urgent or high importance
        // 2. It has a due date
        // 3. It contains action keywords

        boolean requiresAction = false;

        if (email.getImportance() == Email.ImportanceLevel.URGENT ||
                email.getImportance() == Email.ImportanceLevel.HIGH) {
            requiresAction = true;
        }

        if (email.getDueDate() != null) {
            requiresAction = true;
        }

        // Check for action keywords
        String bodyLower = email.getBodyPlainText() != null ? email.getBodyPlainText().toLowerCase() : "";
        String subjectLower = email.getSubject().toLowerCase();

        String[] actionKeywords = {
                "please respond", "action required", "urgent", "asap",
                "deadline", "due date", "respond by", "reply by",
                "approval needed", "review required", "feedback needed"
        };

        for (String keyword : actionKeywords) {
            if (bodyLower.contains(keyword) || subjectLower.contains(keyword)) {
                requiresAction = true;
                break;
            }
        }

        email.setRequiresAction(requiresAction);
    }
}
