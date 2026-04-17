package com.emailmanager.controller;

import com.emailmanager.entity.EmailAccount;
import com.emailmanager.repository.EmailAccountRepository;
import com.emailmanager.service.EmailSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST API controller for email accounts
 */
@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class EmailAccountController {

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
}
