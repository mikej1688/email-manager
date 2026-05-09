package com.emailmanager.controller;

import com.emailmanager.entity.Email;
import com.emailmanager.entity.EmailAccount;
import com.emailmanager.entity.EmailAttachment;
import com.emailmanager.entity.EmailFolder;
import com.emailmanager.repository.EmailAccountRepository;
import com.emailmanager.repository.EmailAttachmentRepository;
import com.emailmanager.repository.EmailFolderRepository;
import com.emailmanager.repository.EmailRepository;
import com.emailmanager.repository.NotificationRepository;
import com.emailmanager.service.AuditLogService;
import com.emailmanager.service.RecipientAddressService;
import com.emailmanager.service.email.GmailService;
import com.emailmanager.service.email.ImapEmailService;
import com.emailmanager.service.email.RecipientListUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.time.LocalDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/emails")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j
public class EmailController {

    private final EmailRepository emailRepository;
    private final EmailAttachmentRepository emailAttachmentRepository;
    private final EmailAccountRepository emailAccountRepository;
    private final EmailFolderRepository emailFolderRepository;
    private final NotificationRepository notificationRepository;
    private final RecipientAddressService recipientAddressService;
    private final GmailService gmailService;
    private final ImapEmailService imapEmailService;
    private final AuditLogService auditLogService;

    @GetMapping
    public ResponseEntity<Page<Email>> getAllEmails(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "receivedDate") String sortBy,
            @RequestParam(defaultValue = "DESC") String direction) {

        Sort.Direction sortDirection = direction.equalsIgnoreCase("ASC") ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sortBy));
        return ResponseEntity.ok(emailRepository.findAll(pageable));
    }

    @GetMapping("/account/{accountId}")
    public ResponseEntity<Page<Email>> getEmailsByAccount(
            @PathVariable Long accountId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return emailAccountRepository.findById(accountId)
                .map(account -> {
                    Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "receivedDate"));
                    return ResponseEntity.ok(emailRepository.findByAccount(account, pageable));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/account/{accountId}/unread")
    public ResponseEntity<Page<Email>> getUnreadEmails(
            @PathVariable Long accountId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return emailAccountRepository.findById(accountId)
                .map(account -> {
                    Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "receivedDate"));
                    return ResponseEntity.ok(emailRepository.findByAccountAndIsReadAndCategoryNotIn(
                            account, false,
                            List.of(Email.EmailCategory.TRASH, Email.EmailCategory.SENT, Email.EmailCategory.DRAFT),
                            pageable));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/account/{accountId}/category/{category}")
    public ResponseEntity<Page<Email>> getEmailsByCategory(
            @PathVariable Long accountId,
            @PathVariable Email.EmailCategory category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return emailAccountRepository.findById(accountId)
                .map(account -> {
                    Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "receivedDate"));
                    return ResponseEntity.ok(
                            emailRepository.findByAccountAndCategoryAndFolderIsNull(account, category, pageable));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/account/{accountId}/folder/{folderId}")
    public ResponseEntity<Page<Email>> getEmailsByFolder(
            @PathVariable Long accountId,
            @PathVariable Long folderId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return emailAccountRepository.findById(accountId)
                .map(account -> emailFolderRepository.findById(folderId)
                        .filter(folder -> folder.getAccount().getId().equals(account.getId()))
                        .map(folder -> {
                            Pageable pageable = PageRequest.of(page, size,
                                    Sort.by(Sort.Direction.DESC, "receivedDate"));
                            Page<Email> emails = folder.getGmailLabelId() != null
                                    ? emailRepository.findByAccountAndGmailLabel(account, folder.getGmailLabelId(),
                                            pageable)
                                    : emailRepository.findByAccountAndFolder(account, folder, pageable);
                            return ResponseEntity.ok(emails);
                        })
                        .orElse(ResponseEntity.notFound().build()))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/account/{accountId}/importance/{importance}")
    public ResponseEntity<Page<Email>> getEmailsByImportance(
            @PathVariable Long accountId,
            @PathVariable Email.ImportanceLevel importance,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return emailAccountRepository.findById(accountId)
                .map(account -> {
                    Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "receivedDate"));
                    return ResponseEntity.ok(
                            emailRepository.findByAccountAndImportance(account, importance, pageable));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Email> getEmailById(@PathVariable Long id, HttpServletRequest request) {
        return emailRepository.findById(id)
                .map(email -> {
                    auditLogService.logEmailRead(id, email.getAccount().getId(), resolveClientIp(request));
                    return ResponseEntity.ok(email);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Search emails for an account by keyword.
     * Encrypted columns cannot be searched with SQL LIKE, so emails are loaded
     * (already decrypted by the JPA converter) and filtered in-memory.
     */
    @GetMapping("/account/{accountId}/search")
    public ResponseEntity<Page<Email>> searchEmails(
            @PathVariable Long accountId,
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {

        if (q == null || q.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        return emailAccountRepository.findById(accountId)
                .map(account -> {
                    String keyword = q.trim().toLowerCase();
                    List<Email> all = emailRepository
                            .findByAccount(account, Pageable.unpaged()).getContent();
                    List<Email> matched = all.stream()
                            .filter(e -> emailMatchesKeyword(e, keyword))
                            .sorted(java.util.Comparator
                                    .comparing(Email::getReceivedDate,
                                            java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())))
                            .toList();
                    auditLogService.logEmailSearch(accountId, keyword.length(), resolveClientIp(request), matched.size());
                    int start = page * size;
                    List<Email> pageContent = start >= matched.size()
                            ? List.of()
                            : matched.subList(start, Math.min(start + size, matched.size()));
                    Page<Email> resultPage = new PageImpl<>(pageContent, PageRequest.of(page, size), matched.size());
                    return ResponseEntity.ok(resultPage);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private boolean emailMatchesKeyword(Email e, String keyword) {
        return containsIgnoreCase(e.getSubject(), keyword)
                || containsIgnoreCase(e.getFromAddress(), keyword)
                || containsIgnoreCase(e.getFromName(), keyword)
                || containsIgnoreCase(e.getToAddresses(), keyword)
                || containsIgnoreCase(e.getBodyPlainText(), keyword);
    }

    private boolean containsIgnoreCase(String field, String keyword) {
        return field != null && field.toLowerCase().contains(keyword);
    }

    @PutMapping("/{id}/mark-read")
    public ResponseEntity<Email> markAsRead(@PathVariable Long id) {
        return emailRepository.findById(id)
                .map(email -> {
                    email.setIsRead(true);
                    return ResponseEntity.ok(emailRepository.save(email));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/mark-unread")
    public ResponseEntity<Email> markAsUnread(@PathVariable Long id) {
        return emailRepository.findById(id)
                .map(email -> {
                    email.setIsRead(false);
                    return ResponseEntity.ok(emailRepository.save(email));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/star")
    public ResponseEntity<Email> starEmail(@PathVariable Long id) {
        return emailRepository.findById(id)
                .map(email -> {
                    email.setIsStarred(true);
                    return ResponseEntity.ok(emailRepository.save(email));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/unstar")
    public ResponseEntity<Email> unstarEmail(@PathVariable Long id) {
        return emailRepository.findById(id)
                .map(email -> {
                    email.setIsStarred(false);
                    return ResponseEntity.ok(emailRepository.save(email));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/refresh-body")
    @Transactional
    public ResponseEntity<Email> refreshEmailBody(@PathVariable Long id) {
        Optional<Email> optional = emailRepository.findById(id);
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
     * Delete email — moves to trash on Gmail, deletes locally.
     */
    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> deleteEmail(@PathVariable Long id, HttpServletRequest request) {
        return emailRepository.findById(id)
                .map(email -> {
                    auditLogService.logEmailDelete(id, email.getAccount().getId(), resolveClientIp(request));
                    try {
                        EmailAccount account = emailAccountRepository
                                .findById(email.getAccount().getId()).orElse(null);
                        if (account != null) {
                            boolean remoteOk;
                            if (account.getProvider() == EmailAccount.EmailProvider.GMAIL) {
                                remoteOk = gmailService.trashEmail(account, email.getMessageId());
                            } else {
                                remoteOk = imapEmailService.deleteEmail(account, email.getMessageId());
                            }
                            if (!remoteOk) {
                                log.warn("Remote delete failed for email {} — not removing locally", id);
                                return ResponseEntity.status(org.springframework.http.HttpStatus.BAD_GATEWAY).<Void>build();
                            }
                        }
                    } catch (Exception e) {
                        log.warn("Remote delete failed for email {}: {}", id, e.getMessage());
                    }
                    notificationRepository.deleteAll(notificationRepository.findByEmail(email));
                    emailRepository.delete(email);
                    return ResponseEntity.ok().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

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
                                gmailService.trashEmail(account, email.getMessageId());
                            } else {
                                imapEmailService.deleteEmail(account, email.getMessageId());
                            }
                        }
                    } catch (Exception e) {
                        // Continue with local update even if remote fails
                    }
                    email.setCategory(Email.EmailCategory.TRASH);
                    return ResponseEntity.ok(emailRepository.save(email));
                })
                .orElse(ResponseEntity.notFound().build());
    }

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
                    return ResponseEntity.ok(emailRepository.save(email));
                })
                .orElse(ResponseEntity.notFound().build());
    }

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
                        email.setFolder(null);
                        email.setCategory(targetCategory);
                        return ResponseEntity.ok(emailRepository.save(email));
                    } catch (IllegalArgumentException e) {
                        return ResponseEntity.<Email>badRequest().build();
                    }
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/account/{accountId}/folders")
    public ResponseEntity<List<EmailFolder>> getFoldersByAccount(@PathVariable Long accountId) {
        return emailAccountRepository.findById(accountId)
                .map(account -> ResponseEntity.ok(emailFolderRepository.findByAccountOrderByDisplayOrder(account)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/account/{accountId}/folders")
    @Transactional
    public ResponseEntity<?> createFolder(@PathVariable Long accountId, @RequestBody Map<String, String> request) {
        String name = request.getOrDefault("name", "").trim();
        if (name.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", "Folder name is required"));
        }

        return emailAccountRepository.findById(accountId)
                .<ResponseEntity<?>>map(account -> {
                    if (emailFolderRepository.findByAccountAndName(account, name).isPresent()) {
                        return ResponseEntity.badRequest().body(Map.of(
                                "status", "error",
                                "message", "A folder with that name already exists"));
                    }

                    EmailFolder folder = new EmailFolder();
                    folder.setAccount(emailAccountRepository.getReferenceById(account.getId()));
                    folder.setName(name);
                    folder.setDescription(request.getOrDefault("description", ""));
                    folder.setFolderPath(name);
                    folder.setIsSystemFolder(false);
                    folder.setDisplayOrder(emailFolderRepository.findByAccount(account).size());

                    return ResponseEntity.ok(emailFolderRepository.save(folder));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/move-to-folder")
    @Transactional
    public ResponseEntity<Email> moveEmailToCustomFolder(@PathVariable Long id, @RequestParam Long folderId) {
        Optional<Email> emailOptional = emailRepository.findById(id);
        if (emailOptional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Optional<EmailFolder> folderOptional = emailFolderRepository.findById(folderId);
        if (folderOptional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Email email = emailOptional.get();
        EmailFolder folder = folderOptional.get();

        if (!folder.getAccount().getId().equals(email.getAccount().getId())) {
            return ResponseEntity.badRequest().build();
        }

        try {
            EmailAccount account = emailAccountRepository
                    .findById(email.getAccount().getId()).orElse(null);
            if (account != null) {
                if (account.getProvider() == EmailAccount.EmailProvider.GMAIL) {
                    gmailService.moveToFolder(account, email.getMessageId(), folder.getName());
                } else {
                    imapEmailService.moveToFolder(account, email.getMessageId(), folder.getName());
                }
            }
        } catch (Exception e) {
            // Continue with local update even if remote move fails.
        }

        email.setFolder(folder);
        return ResponseEntity.ok(emailRepository.save(email));
    }

    @GetMapping("/recipient-suggestions")
    public ResponseEntity<List<String>> getRecipientSuggestions(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "8") int limit) {
        return ResponseEntity.ok(recipientAddressService.getSuggestions(q, limit));
    }

    @PostMapping("/drafts")
    @Transactional
    public ResponseEntity<Map<String, String>> saveDraft(@RequestBody Map<String, String> request) {
        String accountIdValue = request.get("accountId");
        if (accountIdValue == null || accountIdValue.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", "Account is required"));
        }

        Long accountId = Long.parseLong(accountIdValue);
        return emailAccountRepository.findById(accountId)
                .map(account -> {
                    Email draft = null;
                    String draftIdValue = request.get("draftId");
                    if (draftIdValue != null && !draftIdValue.isBlank()) {
                        draft = emailRepository.findById(Long.parseLong(draftIdValue)).orElse(null);
                    }

                    if (draft == null) {
                        draft = new Email();
                        draft.setMessageId("draft-" + UUID.randomUUID());
                        draft.setAccount(emailAccountRepository.getReferenceById(account.getId()));
                        draft.setCategory(Email.EmailCategory.DRAFT);
                        draft.setIsRead(true);
                    }

                    draft.setFromAddress(account.getEmailAddress());
                    draft.setFromName(account.getDisplayName());
                    draft.setToAddresses(request.getOrDefault("to", ""));
                    draft.setCcAddresses(request.getOrDefault("cc", ""));
                    draft.setSubject(request.getOrDefault("subject", ""));
                    draft.setBodyPlainText(request.getOrDefault("body", ""));
                    draft.setReceivedDate(LocalDateTime.now());
                    draft.setIsStarred(false);
                    draft.setImportance(Email.ImportanceLevel.NORMAL);
                    draft.setIsSpam(false);
                    draft.setIsPhishing(false);

                    Email savedDraft = emailRepository.save(draft);
                    return ResponseEntity.ok(Map.of(
                            "status", "saved",
                            "message", "Draft saved",
                            "draftId", String.valueOf(savedDraft.getId())));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/drafts/{id}")
    @Transactional
    public ResponseEntity<Map<String, String>> discardDraft(@PathVariable Long id) {
        return emailRepository.findById(id)
                .map(draft -> {
                    if (draft.getCategory() != Email.EmailCategory.DRAFT) {
                        return ResponseEntity.badRequest().body(Map.of(
                                "status", "error",
                                "message", "Email is not a draft"));
                    }

                    emailRepository.delete(draft);
                    return ResponseEntity.ok(Map.of(
                            "status", "deleted",
                            "message", "Draft discarded"));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/send")
    public ResponseEntity<Map<String, String>> sendEmail(@RequestBody Map<String, String> request,
            HttpServletRequest httpRequest) {
        Long accountId = Long.parseLong(request.get("accountId"));
        String to = request.get("to");
        String cc = request.getOrDefault("cc", "");
        String subject = request.get("subject");
        String body = request.get("body");
        String draftIdValue = request.getOrDefault("draftId", "");
        boolean isHtml = Boolean.parseBoolean(request.getOrDefault("isHtml", "false"));

        return emailAccountRepository.findById(accountId)
                .map(account -> {
                    boolean success;
                    Map<String, String> result = new HashMap<>();
                    if (account.getProvider() == EmailAccount.EmailProvider.GMAIL) {
                        try {
                            success = gmailService.sendComposedEmail(account, to, cc, subject, body, isHtml);
                        } catch (RuntimeException e) {
                            result.put("status", "error");
                            result.put("message", e.getMessage() != null ? e.getMessage() : "Failed to send email");
                            return ResponseEntity.<Map<String, String>>internalServerError().body(result);
                        }
                    } else {
                        success = imapEmailService.sendEmail(account, to, subject, body);
                    }
                    if (success) {
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
                        deleteDraftIfPresent(draftIdValue);
                        auditLogService.logEmailSend(sent.getId(), accountId, resolveClientIp(httpRequest));

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

    @PostMapping("/{id}/reply")
    public ResponseEntity<Map<String, String>> replyToEmail(@PathVariable Long id,
            @RequestBody Map<String, String> request, HttpServletRequest httpRequest) {
        return emailRepository.findById(id)
                .<ResponseEntity<Map<String, String>>>map(email -> {
                    String to = request.getOrDefault("to", email.getFromAddress());
                    String body = request.get("body");
                    String cc = request.getOrDefault("cc", "");
                    String draftIdValue = request.getOrDefault("draftId", "");
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
                        String replySubject = email.getSubject().startsWith("Re:") ? email.getSubject()
                                : "Re: " + email.getSubject();
                        String replyRecipients = replyAll
                                ? mergeReplyAllRecipients(to, email.getToAddresses(), account.getEmailAddress())
                                : to;
                        success = imapEmailService.sendEmail(account, replyRecipients, replySubject, body);
                    }

                    Map<String, String> result = new HashMap<>();
                    if (success) {
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
                        deleteDraftIfPresent(draftIdValue);
                        auditLogService.logEmailReply(id, account.getId(), resolveClientIp(httpRequest));

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

    @PostMapping("/{id}/forward")
    public ResponseEntity<Map<String, String>> forwardEmail(@PathVariable Long id,
            @RequestBody Map<String, String> request, HttpServletRequest httpRequest) {
        return emailRepository.findById(id)
                .<ResponseEntity<Map<String, String>>>map(email -> {
                    String to = request.get("to");
                    String body = request.getOrDefault("body", "");
                    String draftIdValue = request.getOrDefault("draftId", "");
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
                        deleteDraftIfPresent(draftIdValue);
                        auditLogService.logEmailForward(id, account.getId(), resolveClientIp(httpRequest));

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

    private void deleteDraftIfPresent(String draftIdValue) {
        if (draftIdValue == null || draftIdValue.isBlank()) {
            return;
        }

        try {
            Long draftId = Long.parseLong(draftIdValue);
            emailRepository.findById(draftId)
                    .filter(email -> email.getCategory() == Email.EmailCategory.DRAFT)
                    .ifPresent(emailRepository::delete);
        } catch (NumberFormatException ignored) {
        }
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
            case DRAFT:
                return "DRAFT";
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

    @GetMapping("/{emailId}/attachments")
    @Transactional
    public ResponseEntity<List<Map<String, Object>>> listAttachments(@PathVariable Long emailId) {
        Optional<Email> emailOpt = emailRepository.findById(emailId);
        if (emailOpt.isEmpty()) return ResponseEntity.notFound().build();

        Email email = emailOpt.get();
        List<EmailAttachment> attachments = emailAttachmentRepository.findByEmailId(emailId);

        // Auto-extract from Gmail for emails that were synced before attachment support was added
        if (attachments.isEmpty()
                && email.getAccount().getProvider() == EmailAccount.EmailProvider.GMAIL) {
            try {
                gmailService.fetchAndStoreAttachments(email.getAccount(), email);
                emailRepository.save(email);
                attachments = emailAttachmentRepository.findByEmailId(emailId);
            } catch (Exception e) {
                log.warn("Could not auto-fetch attachments for email {}: {}", emailId, e.getMessage());
            }
        }

        List<Map<String, Object>> result = attachments.stream().map(a -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.getId());
            m.put("filename", a.getFilename());
            m.put("contentType", a.getContentType());
            m.put("size", a.getSize());
            return m;
        }).toList();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{emailId}/attachments/{attachmentId}/download")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> downloadAttachment(
            @PathVariable Long emailId, @PathVariable Long attachmentId) {
        return emailAttachmentRepository.findByIdAndEmailId(attachmentId, emailId)
                .map(a -> {
                    byte[] data = a.getData();
                    if (data == null) data = new byte[0];
                    HttpHeaders headers = new HttpHeaders();
                    try {
                        headers.setContentType(MediaType.parseMediaType(a.getContentType()));
                    } catch (Exception e) {
                        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
                    }
                    headers.setContentDispositionFormData("attachment", a.getFilename());
                    headers.setContentLength(data.length);
                    return ResponseEntity.ok().headers(headers).body(data);
                })
                .orElse(ResponseEntity.notFound().build());
    }

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
