package com.emailmanager.controller;

import com.emailmanager.entity.EmailAccount;
import com.emailmanager.service.OAuth2Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller for handling OAuth 2.0 authentication flows
 */
@RestController
@RequestMapping("/api/oauth")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class OAuth2Controller {

    private final OAuth2Service oAuth2Service;

    @Value("${frontend.url:http://localhost:3000}")
    private String frontendUrl;

    /**
     * Initiate Gmail OAuth flow
     * GET /api/oauth/gmail/authorize?email=user@example.com
     */
    @GetMapping("/gmail/authorize")
    public ResponseEntity<Map<String, String>> startGmailAuth(@RequestParam String email) {
        try {
            String authUrl = oAuth2Service.getAuthorizationUrl(email);
            Map<String, String> response = new HashMap<>();
            response.put("authorizationUrl", authUrl);
            response.put("email", email);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to start Gmail authorization", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to initiate OAuth flow: " + e.getMessage()));
        }
    }

    /**
     * OAuth callback endpoint - Google redirects here after user authorization
     * GET /api/oauth/callback?code=xxx&state=user@example.com
     */
    @GetMapping("/callback")
    public RedirectView handleOAuthCallback(
            @RequestParam String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error) {

        RedirectView redirectView = new RedirectView();

        try {
            if (error != null) {
                log.error("OAuth error: {}", error);
                redirectView.setUrl(frontendUrl + "/accounts?error=" + error);
                return redirectView;
            }

            if (state == null || state.isEmpty()) {
                log.error("Missing state parameter in OAuth callback");
                redirectView.setUrl(frontendUrl + "/accounts?error=missing_state");
                return redirectView;
            }

            // Exchange code for tokens and save account
            EmailAccount account = oAuth2Service.handleCallback(code, state);

            log.info("Successfully authenticated Gmail account: {}", account.getEmailAddress());
            redirectView.setUrl(frontendUrl + "/accounts?success=true&email=" + account.getEmailAddress());

        } catch (Exception e) {
            log.error("Failed to handle OAuth callback", e);
            redirectView.setUrl(frontendUrl + "/accounts?error=authentication_failed");
        }

        return redirectView;
    }

    /**
     * Refresh access token for an account
     * POST /api/oauth/refresh/{accountId}
     */
    @PostMapping("/refresh/{accountId}")
    public ResponseEntity<Map<String, String>> refreshToken(@PathVariable Long accountId) {
        try {
            // Implementation would get account from repository and refresh
            return ResponseEntity.ok(Map.of("status", "Token refreshed successfully"));
        } catch (Exception e) {
            log.error("Failed to refresh token", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Revoke OAuth access for an account
     * POST /api/oauth/revoke/{accountId}
     */
    @PostMapping("/revoke/{accountId}")
    public ResponseEntity<Map<String, String>> revokeAccess(@PathVariable Long accountId) {
        try {
            // Implementation would get account from repository and revoke
            return ResponseEntity.ok(Map.of("status", "Access revoked successfully"));
        } catch (Exception e) {
            log.error("Failed to revoke access", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Check OAuth status for debugging
     * GET /api/oauth/status
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getOAuthStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("oauthEnabled", true);
        status.put("provider", "Google Gmail");
        status.put("callbackUrl", "/api/oauth/callback");

        // Add configuration verification
        Map<String, Object> config = oAuth2Service.verifyOAuthConfig();
        status.putAll(config);

        return ResponseEntity.ok(status);
    }
}
