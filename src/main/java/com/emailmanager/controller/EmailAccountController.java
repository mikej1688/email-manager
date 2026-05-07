package com.emailmanager.controller;

import com.emailmanager.entity.EmailAccount;
import com.emailmanager.entity.User;
import com.emailmanager.service.EmailAccountService;
import com.emailmanager.service.EmailSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class EmailAccountController {

    private final EmailAccountService emailAccountService;
    private final EmailSyncService emailSyncService;

    /** List all accounts belonging to the authenticated user (ADMIN sees all). */
    @GetMapping
    public ResponseEntity<List<EmailAccount>> getAllAccounts(@AuthenticationPrincipal User user) {
        List<EmailAccount> accounts = user.getRole() == User.Role.ADMIN
                ? emailAccountService.getAllAccounts()
                : emailAccountService.getAccountsForUser(user);
        return ResponseEntity.ok(accounts);
    }

    /** Active accounts for the authenticated user (ADMIN sees all). */
    @GetMapping("/active")
    public ResponseEntity<List<EmailAccount>> getActiveAccounts(@AuthenticationPrincipal User user) {
        List<EmailAccount> accounts = user.getRole() == User.Role.ADMIN
                ? emailAccountService.getActiveAccounts()
                : emailAccountService.getActiveAccountsForUser(user);
        return ResponseEntity.ok(accounts);
    }

    /** Get a single account — only if it belongs to the caller (or caller is ADMIN). */
    @GetMapping("/{id}")
    public ResponseEntity<EmailAccount> getAccountById(
            @PathVariable Long id,
            @AuthenticationPrincipal User user) {
        return emailAccountService.findById(id)
                .filter(a -> canAccess(user, a))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** Add a new account — owned by the authenticated user. */
    @PostMapping
    public ResponseEntity<EmailAccount> addAccount(
            @RequestBody EmailAccount account,
            @AuthenticationPrincipal User user) {
        account.setOwner(user);
        EmailAccount saved = emailAccountService.createAccount(account);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    /** Update an account — only the owner or ADMIN. */
    @PutMapping("/{id}")
    public ResponseEntity<EmailAccount> updateAccount(
            @PathVariable Long id,
            @RequestBody EmailAccount account,
            @AuthenticationPrincipal User user) {
        Optional<EmailAccount> existing = emailAccountService.findById(id)
                .filter(a -> canAccess(user, a));
        if (existing.isEmpty()) return ResponseEntity.notFound().build();
        return emailAccountService.updateAccount(id, account)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** Delete an account — only the owner or ADMIN. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAccount(
            @PathVariable Long id,
            @AuthenticationPrincipal User user) {
        boolean owned = emailAccountService.findById(id)
                .filter(a -> canAccess(user, a))
                .isPresent();
        if (!owned) return ResponseEntity.notFound().build();
        emailAccountService.deleteAccount(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/test-connection")
    public ResponseEntity<Map<String, Object>> testConnectionForDraft(@RequestBody EmailAccount account) {
        EmailAccount hydrated = emailAccountService.createAccountPreview(account);
        boolean connected = emailSyncService.testConnection(hydrated);
        Map<String, Object> result = new HashMap<>();
        result.put("success", connected);
        if (connected) {
            result.put("message", "Connection successful.");
        } else if (hydrated.getProvider() == EmailAccount.EmailProvider.YAHOO) {
            result.put("message",
                    "Yahoo rejected the login. Use a Yahoo app password instead of your regular sign-in password.");
        } else {
            result.put("message", "Could not connect with these settings.");
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{id}/test-connection")
    public ResponseEntity<Boolean> testConnection(
            @PathVariable Long id,
            @AuthenticationPrincipal User user) {
        return emailAccountService.findById(id)
                .filter(a -> canAccess(user, a))
                .map(account -> ResponseEntity.ok(emailSyncService.testConnection(account)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/sync")
    public ResponseEntity<String> syncAccount(
            @PathVariable Long id,
            @AuthenticationPrincipal User user) {
        return emailAccountService.findById(id)
                .filter(a -> canAccess(user, a))
                .map(account -> {
                    emailSyncService.syncAccount(account);
                    return ResponseEntity.ok("Sync completed successfully");
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/full-resync")
    public ResponseEntity<String> fullResyncAccount(
            @PathVariable Long id,
            @AuthenticationPrincipal User user) {
        return emailAccountService.findById(id)
                .filter(a -> canAccess(user, a))
                .map(account -> {
                    emailSyncService.fullResyncAccount(account);
                    return ResponseEntity.ok("Full re-sync completed successfully");
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /** ADMIN only: sync all active accounts across all users. */
    @PostMapping("/sync-all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> syncAllAccounts() {
        emailSyncService.syncAllAccounts();
        return ResponseEntity.ok("Sync initiated for all accounts");
    }

    // Returns true if the user owns the account or is ADMIN.
    private boolean canAccess(User user, EmailAccount account) {
        if (user.getRole() == User.Role.ADMIN) return true;
        return account.getOwner() != null && account.getOwner().getId().equals(user.getId());
    }
}
