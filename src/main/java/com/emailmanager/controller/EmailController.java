package com.emailmanager.controller;

import com.emailmanager.entity.Email;
import com.emailmanager.entity.EmailAccount;
import com.emailmanager.repository.EmailAccountRepository;
import com.emailmanager.repository.EmailRepository;
import com.emailmanager.service.email.GmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST API controller for emails
 */
@RestController
@RequestMapping("/api/emails")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class EmailController {

    private final EmailRepository emailRepository;
    private final EmailAccountRepository emailAccountRepository;
    private final GmailService gmailService;

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
                    Page<Email> emails = emailRepository.findByAccount(account, pageable);
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
                    Page<Email> emails = emailRepository.findByAccountAndIsRead(account, false, pageable);
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
     * Delete email
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEmail(@PathVariable Long id) {
        return emailRepository.findById(id)
                .map(email -> {
                    emailRepository.delete(email);
                    return ResponseEntity.ok().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
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
