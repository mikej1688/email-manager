package com.emailmanager.controller;

import com.emailmanager.entity.EmailAccount;
import com.emailmanager.repository.EmailAccountRepository;
import com.emailmanager.service.EmailSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST API controller for email accounts
 */
@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class EmailAccountController {

    private static final String YAHOO_IMAP_SERVER = "imap.mail.yahoo.com";
    private static final int YAHOO_IMAP_PORT = 993;
    private static final String YAHOO_SMTP_SERVER = "smtp.mail.yahoo.com";
    private static final int YAHOO_SMTP_PORT = 587;

    private final EmailAccountRepository emailAccountRepository;
    private final EmailSyncService emailSyncService;

    /**
     * Get all email accounts
     */
    @GetMapping
    public ResponseEntity<List<EmailAccount>> getAllAccounts() {
        List<EmailAccount> accounts = emailAccountRepository.findAll();
        return ResponseEntity.ok(accounts);
    }

    /**
     * Get active email accounts
     */
    @GetMapping("/active")
    public ResponseEntity<List<EmailAccount>> getActiveAccounts() {
        List<EmailAccount> accounts = emailAccountRepository.findByIsActiveTrue();
        return ResponseEntity.ok(accounts);
    }

    /**
     * Get email account by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<EmailAccount> getAccountById(@PathVariable Long id) {
        return emailAccountRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Add new email account
     */
    @PostMapping
    public ResponseEntity<EmailAccount> addAccount(@RequestBody EmailAccount account) {
        applyProviderDefaults(account);
        EmailAccount savedAccount = emailAccountRepository.save(account);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedAccount);
    }

    /**
     * Update email account
     */
    @PutMapping("/{id}")
    public ResponseEntity<EmailAccount> updateAccount(
            @PathVariable Long id,
            @RequestBody EmailAccount account) {

        return emailAccountRepository.findById(id)
                .map(existingAccount -> {
                    account.setId(id);
                    applyProviderDefaults(account);
                    EmailAccount updated = emailAccountRepository.save(account);
                    return ResponseEntity.ok(updated);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Delete email account
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAccount(@PathVariable Long id) {
        return emailAccountRepository.findById(id)
                .map(account -> {
                    emailAccountRepository.delete(account);
                    return ResponseEntity.ok().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Test connection to email account
     */
    @PostMapping("/test-connection")
    public ResponseEntity<Map<String, Object>> testConnectionForDraft(@RequestBody EmailAccount account) {
        applyProviderDefaults(account);
        boolean connected = emailSyncService.testConnection(account);
        Map<String, Object> result = new HashMap<>();
        result.put("success", connected);
        if (connected) {
            result.put("message", "Connection successful.");
        } else if (account.getProvider() == EmailAccount.EmailProvider.YAHOO) {
            result.put("message",
                    "Yahoo rejected the login. Use a Yahoo app password instead of your regular sign-in password, then try again.");
        } else {
            result.put("message", "Could not connect with these settings.");
        }
        return ResponseEntity.ok(result);
    }

    /**
     * Test connection to saved email account
     */
    @PostMapping("/{id}/test-connection")
    public ResponseEntity<Boolean> testConnection(@PathVariable Long id) {
        return emailAccountRepository.findById(id)
                .map(account -> {
                    boolean connected = emailSyncService.testConnection(account);
                    return ResponseEntity.ok(connected);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Sync specific email account
     */
    @PostMapping("/{id}/sync")
    public ResponseEntity<String> syncAccount(@PathVariable Long id) {
        return emailAccountRepository.findById(id)
                .map(account -> {
                    emailSyncService.syncAccount(account);
                    return ResponseEntity.ok("Sync completed successfully");
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Sync all active accounts
     */
    @PostMapping("/sync-all")
    public ResponseEntity<String> syncAllAccounts() {
        emailSyncService.syncAllAccounts();
        return ResponseEntity.ok("Sync initiated for all accounts");
    }

    private void applyProviderDefaults(EmailAccount account) {
        if (account.getProvider() != EmailAccount.EmailProvider.YAHOO) {
            return;
        }

        if (account.getImapServer() == null || account.getImapServer().isBlank()) {
            account.setImapServer(YAHOO_IMAP_SERVER);
        }
        if (account.getImapPort() == null || account.getImapPort() <= 0) {
            account.setImapPort(YAHOO_IMAP_PORT);
        }
        if (account.getSmtpServer() == null || account.getSmtpServer().isBlank()) {
            account.setSmtpServer(YAHOO_SMTP_SERVER);
        }
        if (account.getSmtpPort() == null || account.getSmtpPort() <= 0) {
            account.setSmtpPort(YAHOO_SMTP_PORT);
        }
    }
}
