package com.emailmanager.controller;

import com.emailmanager.entity.EmailAccount;
import com.emailmanager.service.EmailAccountService;
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

    private final EmailAccountService emailAccountService;
    private final EmailSyncService emailSyncService;

    /**
     * Get all email accounts
     */
    @GetMapping
    public ResponseEntity<List<EmailAccount>> getAllAccounts() {
        List<EmailAccount> accounts = emailAccountService.getAllAccounts();
        return ResponseEntity.ok(accounts);
    }

    /**
     * Get active email accounts
     */
    @GetMapping("/active")
    public ResponseEntity<List<EmailAccount>> getActiveAccounts() {
        List<EmailAccount> accounts = emailAccountService.getActiveAccounts();
        return ResponseEntity.ok(accounts);
    }

    /**
     * Get email account by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<EmailAccount> getAccountById(@PathVariable Long id) {
        return emailAccountService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Add new email account
     */
    @PostMapping
    public ResponseEntity<EmailAccount> addAccount(@RequestBody EmailAccount account) {
        EmailAccount savedAccount = emailAccountService.createAccount(account);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedAccount);
    }

    /**
     * Update email account
     */
    @PutMapping("/{id}")
    public ResponseEntity<EmailAccount> updateAccount(
            @PathVariable Long id,
            @RequestBody EmailAccount account) {

        return emailAccountService.updateAccount(id, account)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Delete email account
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAccount(@PathVariable Long id) {
        if (emailAccountService.deleteAccount(id)) {
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
    }

    /**
     * Test connection to email account
     */
    @PostMapping("/test-connection")
    public ResponseEntity<Map<String, Object>> testConnectionForDraft(@RequestBody EmailAccount account) {
        EmailAccount hydratedAccount = emailAccountService.createAccountPreview(account);
        boolean connected = emailSyncService.testConnection(hydratedAccount);
        Map<String, Object> result = new HashMap<>();
        result.put("success", connected);
        if (connected) {
            result.put("message", "Connection successful.");
        } else if (hydratedAccount.getProvider() == EmailAccount.EmailProvider.YAHOO) {
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
        return emailAccountService.findById(id)
                .map(account -> {
                    boolean connected = emailSyncService.testConnection(account);
                    return ResponseEntity.ok(connected);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Sync specific email account (incremental — only fetches emails newer than
     * the last sync). Used by background polling in the frontend.
     */
    @PostMapping("/{id}/sync")
    public ResponseEntity<String> syncAccount(@PathVariable Long id) {
        return emailAccountService.findById(id)
                .map(account -> {
                    emailSyncService.syncAccount(account);
                    return ResponseEntity.ok("Sync completed successfully");
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Force a full re-sync for a Gmail account — resets the incremental-sync
     * state so every email in every label is fetched from Gmail from the
     * beginning, not just emails newer than the last sync time.
     */
    @PostMapping("/{id}/full-resync")
    public ResponseEntity<String> fullResyncAccount(@PathVariable Long id) {
        return emailAccountService.findById(id)
                .map(account -> {
                    emailSyncService.fullResyncAccount(account);
                    return ResponseEntity.ok("Full re-sync completed successfully");
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
}
