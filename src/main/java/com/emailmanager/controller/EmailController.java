package com.emailmanager.controller;

import com.emailmanager.entity.Email;
import com.emailmanager.entity.EmailAccount;
import com.emailmanager.repository.EmailAccountRepository;
import com.emailmanager.repository.EmailRepository;
import com.emailmanager.service.RecipientAddressService;
import com.emailmanager.service.email.GmailService;
import com.emailmanager.service.email.ImapEmailService;
import com.emailmanager.service.email.RecipientListUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * REST API controller for emails
 */
@RestController
@RequestMapping("/api/emails")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j
public class EmailController {

    private final EmailRepository emailRepository;
    private final EmailAccountRepository emailAccountRepository;
    private final RecipientAddressService recipientAddressService;
    private final GmailService gmailService;
    private final ImapEmailService imapEmailService;

    /**
     * Get all emails with pagination
     */
    @GetMapping
    public ResponseEntity<Page<Email>> getAllEmails(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "receivedDate") String sortBy,
            @RequestParam(defaultValue = "DESC") String direction) {

        Sort.Direction sortDirection = direction.equalsIgnoreCase("ASC") ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sortBy));

        Page<Email> emails = emailRepository.findAll(pageable);
        return ResponseEntity.ok(emails);
    }

    /**
     * Get emails by account
     */
    @GetMapping("/account/{accountId}")
    public ResponseEntity<Page<Email>> getEmailsByAccount(
            @PathVariable Long accountId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return emailAccountRepository.findById(accountId)
                .map(account -> {
                    Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "receivedDate"));
                    Page<Email> emails = emailRepository.findByAccountAndCategoryNotIn(
                            account,
                            List.of(Email.EmailCategory.TRASH, Email.EmailCategory.SENT),
                            pageable);
                    return ResponseEntity.ok(emails);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get unread emails by account
     */
    @GetMapping("/account/{accountId}/unread")
    public ResponseEntity<Page<Email>> getUnreadEmails(
            @PathVariable Long accountId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return emailAccountRepository.findById(accountId)
                .map(account -> {
                    Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "receivedDate"));
                    Page<Email> emails = emailRepository.findByAccountAndIsReadAndCategoryNotIn(
                            account, false,
                            List.of(Email.EmailCategory.TRASH, Email.EmailCategory.SENT),
                            pageable);
                    return ResponseEntity.ok(emails);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get emails by category
     */
    @GetMapping("/account/{accountId}/category/{category}")
    public ResponseEntity<Page<Email>> getEmailsByCategory(
            @PathVariable Long accountId,
            @PathVariable Email.EmailCategory category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return emailAccountRepository.findById(accountId)
                .map(account -> {
                    Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "receivedDate"));
                    Page<Email> emails = emailRepository.findByAccountAndCategory(account, category, pageable);
                    return ResponseEntity.ok(emails);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get emails by importance
     */
    @GetMapping("/account/{accountId}/importance/{importance}")
    public ResponseEntity<Page<Email>> getEmailsByImportance(
            @PathVariable Long accountId,
            @PathVariable Email.ImportanceLevel importance,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return emailAccountRepository.findById(accountId)
                .map(account -> {
                    Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "receivedDate"));
                    Page<Email> emails = emailRepository.findByAccountAndImportance(account, importance, pageable);
                    return ResponseEntity.ok(emails);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get email by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<Email> getEmailById(@PathVariable Long id) {
        return emailRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Mark email as read
     */
    @PutMapping("/{id}/mark-read")
    public ResponseEntity<Email> markAsRead(@PathVariable Long id) {
        return emailRepository.findById(id)
                .map(email -> {
                    email.setIsRead(true);
                    Email updated = emailRepository.save(email);
                    return ResponseEntity.ok(updated);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Mark email as unread
     */
    @PutMapping("/{id}/mark-unread")
    public ResponseEntity<Email> markAsUnread(@PathVariable Long id) {
        return emailRepository.findById(id)
                .map(email -> {
                    email.setIsRead(false);
                    Email updated = emailRepository.save(email);
                    return ResponseEntity.ok(updated);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Star email
     */
    @PutMapping("/{id}/star")
    public ResponseEntity<Email> starEmail(@PathVariable Long id) {
        return emailRepository.findById(id)
                .map(email -> {
                    email.setIsStarred(true);
                    Email updated = emailRepository.save(email);
                    return ResponseEntity.ok(updated);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Unstar email
     */
    @PutMapping("/{id}/unstar")
    public ResponseEntity<Email> unstarEmail(@PathVariable Long id) {
        return emailRepository.findById(id)
                .map(email -> {
                    email.setIsStarred(false);
                    Email updated = emailRepository.save(email);
                    return ResponseEntity.ok(updated);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Re-fetch the body of an existing email from Gmail, embedding inline images.
     * Use this to fix emails that were synced before the image-handling
     * improvement.
     */
    @PutMapping("/{id}/refresh-body")
    @Transactional
    public ResponseEntity<Email> refreshEmailBody(@PathVariable Long id) {
        java.util.Optional<Email> optional = emailRepository.findById(id);
        if (optional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Email email = optional.get();
        try {
            EmailAccount account = emailAccountRepository
                    .findById(email.getAccount().getId())
                    .orElseThrow(() -> new RuntimeException("Account not found"));
            gmailService.refreshEmailBody(account, email);
            return ResponseEntity.ok(emailRepository.save(email));
        } catch (Exception e) {
            return ResponseEntity.<Email>internalServerError().build();
        }
    }

    /**
     * Delete email - trash on remote provider and remove locally
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEmail(@PathVariable Long id) {
        return emailRepository.findById(id)
                .map(email -> {
                    // Trash on remote provider
                    try {
                        EmailAccount account = emailAccountRepository
                                .findById(email.getAccount().getId()).orElse(null);
                        if (account != null) {
                            if (account.getProvider() == EmailAccount.EmailProvider.GMAIL) {
                                gmailService.deleteEmail(account, email.getMessageId());
                            } else {
                                imapEmailService.deleteEmail(account, email.getMessageId());
                            }
                        }
                    } catch (Exception e) {
                        // Log but still delete locally
                    }
                    emailRepository.delete(email);
                    return ResponseEntity.ok().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Move email to trash (soft delete - changes category to TRASH)
     */
    @PutMapping("/{id}/trash")
    @Transactional
    public ResponseEntity<Email> trashEmail(@PathVariable Long id) {
        return emailRepository.findById(id)
                .map(email -> {
                    try {
                        EmailAccount account = emailAccountRepository
                                .findById(email.getAccount().getId()).orElse(null);
                        if (account != null) {
                            if (account.getProvider() == EmailAccount.EmailProvider.GMAIL) {
                                gmailService.deleteEmail(account, email.getMessageId());
                            } else {
                                imapEmailService.deleteEmail(account, email.getMessageId());
                            }
                        }
                    } catch (Exception e) {
                        // Continue with local update even if remote fails
                    }
                    email.setCategory(Email.EmailCategory.TRASH);
                    Email updated = emailRepository.save(email);
                    return ResponseEntity.ok(updated);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Archive email
     */
    @PutMapping("/{id}/archive")
    @Transactional
    public ResponseEntity<Email> archiveEmail(@PathVariable Long id) {
        return emailRepository.findById(id)
                .map(email -> {
                    try {
                        EmailAccount account = emailAccountRepository
                                .findById(email.getAccount().getId()).orElse(null);
                        if (account != null && account.getProvider() == EmailAccount.EmailProvider.GMAIL) {
                            gmailService.archiveEmail(account, email.getMessageId());
                        }
                    } catch (Exception e) {
                        // Continue with local update
                    }
                    email.setCategory(Email.EmailCategory.ARCHIVED);
                    Email updated = emailRepository.save(email);
                    return ResponseEntity.ok(updated);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Move email to a category/folder
     */
    @PutMapping("/{id}/move")
    @Transactional
    public ResponseEntity<Email> moveEmail(@PathVariable Long id, @RequestParam String category) {
        return emailRepository.findById(id)
                .<ResponseEntity<Email>>map(email -> {
                    try {
                        Email.EmailCategory targetCategory = Email.EmailCategory.valueOf(category.toUpperCase());
                        try {
                            EmailAccount account = emailAccountRepository
                                    .findById(email.getAccount().getId()).orElse(null);
                            if (account != null) {
                                if (account.getProvider() == EmailAccount.EmailProvider.GMAIL) {
                                    gmailService.moveToFolder(account, email.getMessageId(),
                                            mapCategoryToGmailLabel(targetCategory));
                                } else {
                                    imapEmailService.moveToFolder(account, email.getMessageId(), category);
                                }
                            }
                        } catch (Exception e) {
                            // Continue with local update
                        }
                        email.setCategory(targetCategory);
                        Email updated = emailRepository.save(email);
                        return ResponseEntity.ok(updated);
                    } catch (IllegalArgumentException e) {
                        return ResponseEntity.<Email>badRequest().build();
                    }
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get saved recipient suggestions for compose autocomplete.
     */
    @GetMapping("/recipient-suggestions")
    public ResponseEntity<List<String>> getRecipientSuggestions(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "8") int limit) {
        return ResponseEntity.ok(recipientAddressService.getSuggestions(q, limit));
    }

    /**
     * Send a new email (compose)
     */
    @PostMapping("/send")
    public ResponseEntity<Map<String, String>> sendEmail(@RequestBody Map<String, String> request) {
        Long accountId = Long.parseLong(request.get("accountId"));
        String to = request.get("to");
        String cc = request.getOrDefault("cc", "");
        String subject = request.get("subject");
        String body = request.get("body");
        boolean isHtml = Boolean.parseBoolean(request.getOrDefault("isHtml", "false"));

        return emailAccountRepository.findById(accountId)
                .map(account -> {
                    boolean success;
                    if (account.getProvider() == EmailAccount.EmailProvider.GMAIL) {
                        success = gmailService.sendComposedEmail(account, to, cc, subject, body, isHtml);
                    } else {
                        success = imapEmailService.sendEmail(account, to, subject, body);
                    }
                    Map<String, String> result = new HashMap<>();
                    if (success) {
                        // Save a local copy in Sent folder
                        Email sent = new Email();
                        sent.setAccount(emailAccountRepository.getReferenceById(account.getId()));
                        sent.setMessageId("sent-" + UUID.randomUUID().toString());
                        sent.setSubject(subject);
                        sent.setFromAddress(account.getEmailAddress());
                        sent.setToAddresses(to);
                        sent.setCcAddresses(cc);
                        sent.setBodyPlainText(body);
                        sent.setReceivedDate(LocalDateTime.now());
                        sent.setIsRead(true);
                        sent.setCategory(Email.EmailCategory.SENT);
                        try {
                            emailRepository.save(sent);
                        } catch (Exception e) {
                            log.error("Failed to save sent copy to local DB", e);
                        }

                        recipientAddressService.recordRecipients(to, cc);

                        result.put("status", "sent");
                        result.put("message", "Email sent successfully");
                        return ResponseEntity.ok(result);
                    } else {
                        result.put("status", "error");
                        result.put("message", "Failed to send email");
                        return ResponseEntity.<Map<String, String>>internalServerError().body(result);
                    }
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Reply to an email
     */
    @PostMapping("/{id}/reply")
    public ResponseEntity<Map<String, String>> replyToEmail(@PathVariable Long id,
            @RequestBody Map<String, String> request) {
        return emailRepository.findById(id)
                .<ResponseEntity<Map<String, String>>>map(email -> {
                    String to = request.getOrDefault("to", email.getFromAddress());
                    String body = request.get("body");
                    String cc = request.getOrDefault("cc", "");
                    boolean replyAll = Boolean.parseBoolean(request.getOrDefault("replyAll", "false"));
                    boolean isHtml = Boolean.parseBoolean(request.getOrDefault("isHtml", "false"));

                    EmailAccount account = emailAccountRepository
                            .findById(email.getAccount().getId()).orElse(null);
                    if (account == null) {
                        return ResponseEntity.<Map<String, String>>notFound().build();
                    }

                    boolean success;
                    if (account.getProvider() == EmailAccount.EmailProvider.GMAIL) {
                        success = gmailService.replyToEmail(account, email.getMessageId(),
                                to, email.getToAddresses(),
                                email.getCcAddresses(), email.getSubject(), body, replyAll, isHtml);
                    } else {
                        // For IMAP, send as a new email with Re: prefix
                        String replySubject = email.getSubject().startsWith("Re:") ? email.getSubject()
                                : "Re: " + email.getSubject();
                        String replyRecipients = replyAll
                                ? mergeReplyAllRecipients(to, email.getToAddresses(), account.getEmailAddress())
                                : to;
                        success = imapEmailService.sendEmail(account, replyRecipients, replySubject, body);
                    }

                    Map<String, String> result = new HashMap<>();
                    if (success) {
                        // Save a local copy in Sent folder
                        String replySubjectSaved = email.getSubject().startsWith("Re:") ? email.getSubject()
                                : "Re: " + email.getSubject();
                        String replyTo = replyAll
                                ? mergeReplyAllRecipients(to, email.getToAddresses(), account.getEmailAddress())
                                : to;
                        Email sent = new Email();
                        sent.setAccount(emailAccountRepository.getReferenceById(account.getId()));
                        sent.setMessageId("sent-" + UUID.randomUUID().toString());
                        sent.setSubject(replySubjectSaved);
                        sent.setFromAddress(account.getEmailAddress());
                        sent.setToAddresses(replyTo);
                        sent.setCcAddresses(cc);
                        sent.setBodyPlainText(body);
                        sent.setReceivedDate(LocalDateTime.now());
                        sent.setIsRead(true);
                        sent.setCategory(Email.EmailCategory.SENT);
                        try {
                            emailRepository.save(sent);
                        } catch (Exception e) {
                            log.error("Failed to save reply sent copy to local DB", e);
                        }

                        recipientAddressService.recordRecipients(replyTo, cc);

                        result.put("status", "sent");
                        result.put("message", "Reply sent successfully");
                        return ResponseEntity.ok(result);
                    } else {
                        result.put("status", "error");
                        result.put("message", "Failed to send reply");
                        return ResponseEntity.<Map<String, String>>internalServerError().body(result);
                    }
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Forward an email
     */
    @PostMapping("/{id}/forward")
    public ResponseEntity<Map<String, String>> forwardEmail(@PathVariable Long id,
            @RequestBody Map<String, String> request) {
        return emailRepository.findById(id)
                .<ResponseEntity<Map<String, String>>>map(email -> {
                    String to = request.get("to");
                    String body = request.getOrDefault("body", "");
                    boolean isHtml = Boolean.parseBoolean(request.getOrDefault("isHtml", "false"));

                    EmailAccount account = emailAccountRepository
                            .findById(email.getAccount().getId()).orElse(null);
                    if (account == null) {
                        return ResponseEntity.<Map<String, String>>notFound().build();
                    }

                    String fwdSubject = email.getSubject().startsWith("Fwd:") ? email.getSubject()
                            : "Fwd: " + email.getSubject();
                    String originalContent = email.getBodyHtml() != null ? email.getBodyHtml()
                            : (email.getBodyPlainText() != null ? email.getBodyPlainText() : "");
                    String fullBody = body + "\n\n---------- Forwarded message ----------\n"
                            + "From: " + email.getFromAddress() + "\n"
                            + "Date: " + email.getReceivedDate() + "\n"
                            + "Subject: " + email.getSubject() + "\n"
                            + "To: " + (email.getToAddresses() != null ? email.getToAddresses() : "") + "\n\n"
                            + originalContent;

                    boolean success;
                    if (account.getProvider() == EmailAccount.EmailProvider.GMAIL) {
                        success = gmailService.sendComposedEmail(account, to, "", fwdSubject, fullBody, isHtml);
                    } else {
                        success = imapEmailService.sendEmail(account, to, fwdSubject, fullBody);
                    }

                    Map<String, String> result = new HashMap<>();
                    if (success) {
                        // Save a local copy in Sent folder
                        Email sent = new Email();
                        sent.setAccount(emailAccountRepository.getReferenceById(account.getId()));
                        sent.setMessageId("sent-" + UUID.randomUUID().toString());
                        sent.setSubject(fwdSubject);
                        sent.setFromAddress(account.getEmailAddress());
                        sent.setToAddresses(to);
                        sent.setBodyPlainText(fullBody);
                        sent.setReceivedDate(LocalDateTime.now());
                        sent.setIsRead(true);
                        sent.setCategory(Email.EmailCategory.SENT);
                        try {
                            emailRepository.save(sent);
                        } catch (Exception e) {
                            log.error("Failed to save forward sent copy to local DB", e);
                        }

                        recipientAddressService.recordRecipients(to);

                        result.put("status", "sent");
                        result.put("message", "Email forwarded successfully");
                        return ResponseEntity.ok(result);
                    } else {
                        result.put("status", "error");
                        result.put("message", "Failed to forward email");
                        return ResponseEntity.<Map<String, String>>internalServerError().body(result);
                    }
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private String mergeReplyAllRecipients(String editedTo, String originalTo, String accountEmailAddress) {
        LinkedHashMap<String, String> recipients = new LinkedHashMap<>();
        addRecipients(recipients, editedTo, false, accountEmailAddress);
        addRecipients(recipients, originalTo, true, accountEmailAddress);
        return String.join(", ", recipients.values());
    }

    private void addRecipients(LinkedHashMap<String, String> recipients, String recipientList,
            boolean skipOwnAddress, String accountEmailAddress) {
        if (recipientList == null || recipientList.isBlank()) {
            return;
        }

        for (String part : RecipientListUtils.splitRecipientList(recipientList)) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            String normalized = normalizeRecipient(trimmed);
            if (skipOwnAddress && normalized.equals(normalizeRecipient(accountEmailAddress))) {
                continue;
            }

            recipients.putIfAbsent(normalized, trimmed);
        }
    }

    private String normalizeRecipient(String recipient) {
        return RecipientListUtils.normalizeRecipient(recipient);
    }

    private String mapCategoryToGmailLabel(Email.EmailCategory category) {
        switch (category) {
            case INBOX:
                return "INBOX";
            case IMPORTANT:
                return "IMPORTANT";
            case SPAM:
                return "SPAM";
            case TRASH:
                return "TRASH";
            case SENT:
                return "SENT";
            case SOCIAL:
                return "CATEGORY_SOCIAL";
            case PROMOTIONS:
                return "CATEGORY_PROMOTIONS";
            case UPDATES:
                return "CATEGORY_UPDATES";
            case FORUMS:
                return "CATEGORY_FORUMS";
            default:
                return category.name();
        }
    }

    /**
     * Get email statistics
     */
    @GetMapping("/account/{accountId}/stats")
    public ResponseEntity<Map<String, Object>> getEmailStats(@PathVariable Long accountId) {
        return emailAccountRepository.findById(accountId)
                .map(account -> {
                    Map<String, Object> stats = new HashMap<>();
                    stats.put("unreadCount", emailRepository.countByAccountAndIsReadFalse(account));
                    stats.put("totalCount", emailRepository.count());
                    return ResponseEntity.ok(stats);
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
