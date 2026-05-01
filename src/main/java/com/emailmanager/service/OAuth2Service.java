package com.emailmanager.service;

import com.emailmanager.entity.EmailAccount;
import com.emailmanager.repository.EmailAccountRepository;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.TokenResponseException;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.DataStore;
import com.google.api.client.util.store.MemoryDataStoreFactory;
import com.google.api.services.gmail.GmailScopes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for managing OAuth 2.0 authentication with Google
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OAuth2Service {

    private final EmailAccountRepository emailAccountRepository;
    private final NetHttpTransport httpTransport = new NetHttpTransport();
    private final GsonFactory jsonFactory = GsonFactory.getDefaultInstance();

    @Value("${gmail.client.id}")
    private String clientId;

    @Value("${gmail.client.secret}")
    private String clientSecret;

    @Value("${gmail.redirect.uri:http://localhost:8080/api/oauth/callback}")
    private String redirectUri;

    private static final List<String> SCOPES = Collections.singletonList(GmailScopes.MAIL_GOOGLE_COM);

    /**
     * Verify OAuth credentials are configured
     */
    public Map<String, Object> verifyOAuthConfig() {
        Map<String, Object> config = new HashMap<>();

        boolean clientIdConfigured = clientId != null && !clientId.isEmpty();
        boolean clientSecretConfigured = clientSecret != null && !clientSecret.isEmpty();

        config.put("clientIdConfigured", clientIdConfigured);
        config.put("clientSecretConfigured", clientSecretConfigured);
        config.put("redirectUri", redirectUri);

        if (clientIdConfigured) {
            // Show only first/last few chars for security
            String maskedId = clientId.substring(0, Math.min(15, clientId.length())) + "..." +
                    clientId.substring(Math.max(0, clientId.length() - 15));
            config.put("clientIdPreview", maskedId);
        } else {
            config.put("clientIdPreview", "NOT CONFIGURED");
        }

        if (clientSecretConfigured) {
            config.put("clientSecretPreview", "***" + clientSecret.substring(Math.max(0, clientSecret.length() - 4)));
        } else {
            config.put("clientSecretPreview", "NOT CONFIGURED");
        }

        config.put("allConfigured", clientIdConfigured && clientSecretConfigured);

        log.info("OAuth Configuration Status: clientId={}, clientSecret={}, redirectUri={}",
                clientIdConfigured ? "CONFIGURED" : "MISSING",
                clientSecretConfigured ? "CONFIGURED" : "MISSING",
                redirectUri);

        return config;
    }

    /**
     * Generate OAuth authorization URL for Gmail
     */
    public String getAuthorizationUrl(String userEmail) {
        try {
            log.info("Generating authorization URL for: {}", userEmail);
            log.debug("OAuth Config - ClientID present: {}, ClientSecret present: {}",
                    clientId != null && !clientId.isEmpty(),
                    clientSecret != null && !clientSecret.isEmpty());

            GoogleAuthorizationCodeFlow flow = createFlow();
            String authUrl = flow.newAuthorizationUrl()
                    .setRedirectUri(redirectUri)
                    .setState(userEmail) // Store email in state for callback
                    .set("prompt", "consent") // Force consent screen so refresh token is always returned
                    .set("access_type", "offline")
                    .build();

            log.info("Generated authorization URL successfully");
            return authUrl;
        } catch (Exception e) {
            log.error("Failed to generate authorization URL", e);
            throw new RuntimeException("Failed to generate authorization URL", e);
        }
    }

    /**
     * Handle OAuth callback and exchange code for tokens
     */
    public EmailAccount handleCallback(String authorizationCode, String userEmail) {
        try {
            GoogleAuthorizationCodeFlow flow = createFlow();

            // Exchange authorization code for tokens
            GoogleTokenResponse tokenResponse = flow.newTokenRequest(authorizationCode)
                    .setRedirectUri(redirectUri)
                    .execute();

            // Find or create email account
            EmailAccount account = emailAccountRepository.findByEmailAddress(userEmail)
                    .orElseGet(() -> {
                        EmailAccount newAccount = new EmailAccount();
                        newAccount.setEmailAddress(userEmail);
                        newAccount.setProvider(EmailAccount.EmailProvider.GMAIL);
                        newAccount.setDisplayName(userEmail);
                        newAccount.setIsActive(true);
                        return newAccount;
                    });

            log.info("OAuth callback received tokens - accessToken present: {}, refreshToken present: {}",
                    tokenResponse.getAccessToken() != null,
                    tokenResponse.getRefreshToken() != null);

            // Store tokens — only overwrite refresh token if a new one was provided,
            // since Google only returns a refresh token on the first authorization
            account.setAccessToken(tokenResponse.getAccessToken());
            if (tokenResponse.getRefreshToken() != null) {
                account.setRefreshToken(tokenResponse.getRefreshToken());
            }

            // Calculate token expiry (typically 1 hour)
            if (tokenResponse.getExpiresInSeconds() != null) {
                account.setTokenExpiryDate(
                        LocalDateTime.now().plusSeconds(tokenResponse.getExpiresInSeconds()));
            }

            return emailAccountRepository.save(account);
        } catch (Exception e) {
            log.error("Failed to handle OAuth callback", e);
            throw new RuntimeException("Failed to handle OAuth callback", e);
        }
    }

    /**
     * Get valid credential for an email account, refreshing if necessary.
     * RuntimeExceptions from token checks / refresh are propagated directly so
     * that callers do not produce duplicate error log entries.
     */
    public Credential getCredential(EmailAccount account) {
        // If no tokens at all, the account needs to go through OAuth authorization.
        // This throws directly (no catch) so the message is not re-wrapped.
        if (account.getAccessToken() == null && account.getRefreshToken() == null) {
            throw new RuntimeException(
                    "Account not authorized: " + account.getEmailAddress() +
                            ". Please complete the OAuth authorization flow.");
        }

        // Refresh if access token is missing or about to expire.
        // refreshAccessToken() already logs the error and clears stale tokens;
        // let its RuntimeException propagate without re-wrapping.
        if (account.getAccessToken() == null ||
                (account.getTokenExpiryDate() != null &&
                        account.getTokenExpiryDate().isBefore(LocalDateTime.now().plusMinutes(5)))) {
            refreshAccessToken(account);
        }

        try {
            GoogleAuthorizationCodeFlow flow = createFlow();
            return flow.createAndStoreCredential(
                    new GoogleTokenResponse()
                            .setAccessToken(account.getAccessToken())
                            .setRefreshToken(account.getRefreshToken()),
                    account.getEmailAddress());
        } catch (Exception e) {
            log.error("Failed to build credential object for account: {}", account.getEmailAddress(), e);
            throw new RuntimeException("Failed to get credential", e);
        }
    }

    /**
     * Refresh access token using refresh token
     */
    public void refreshAccessToken(EmailAccount account) {
        if (account.getRefreshToken() == null || account.getRefreshToken().isEmpty()) {
            throw new RuntimeException("No refresh token available for account: " + account.getEmailAddress()
                    + ". Please re-authorize the account.");
        }

        try {
            GoogleAuthorizationCodeFlow flow = createFlow();

            // Build a credential with the stored tokens
            Credential credential = new Credential.Builder(
                    com.google.api.client.auth.oauth2.BearerToken.authorizationHeaderAccessMethod())
                    .setTransport(httpTransport)
                    .setJsonFactory(jsonFactory)
                    .setTokenServerUrl(new com.google.api.client.http.GenericUrl(flow.getTokenServerEncodedUrl()))
                    .setClientAuthentication(flow.getClientAuthentication())
                    .build()
                    .setAccessToken(account.getAccessToken())
                    .setRefreshToken(account.getRefreshToken());

            boolean refreshed = credential.refreshToken();
            if (!refreshed || credential.getAccessToken() == null) {
                // Refresh returned false — token is invalid; clear it so the account shows
                // as needing re-authorization rather than retrying indefinitely.
                clearTokens(account);
                throw new RuntimeException("Token refresh failed for: " + account.getEmailAddress()
                        + ". Please re-authorize the account via OAuth.");
            }

            // Update account with new token
            account.setAccessToken(credential.getAccessToken());
            if (credential.getExpiresInSeconds() != null) {
                account.setTokenExpiryDate(
                        LocalDateTime.now().plusSeconds(credential.getExpiresInSeconds()));
            }

            emailAccountRepository.save(account);
            log.info("Successfully refreshed access token for: {}", account.getEmailAddress());

        } catch (TokenResponseException e) {
            // Google returned an error (e.g. invalid_grant = token revoked or expired).
            // Clear the stale tokens so the UI can prompt for re-authorization.
            String errorCode = e.getDetails() != null ? e.getDetails().getError() : "unknown";
            log.error("Token refresh rejected by Google for: {} (error={}). Account needs re-authorization.",
                    account.getEmailAddress(), errorCode);
            clearTokens(account);
            throw new RuntimeException("Google rejected the refresh token for: " + account.getEmailAddress()
                    + " (" + errorCode + "). Please re-authorize the account.", e);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to refresh access token for: {}", account.getEmailAddress(), e);
            throw new RuntimeException("Failed to refresh access token for: " + account.getEmailAddress(), e);
        }
    }

    /**
     * Clear stored tokens and deactivate the account when they become invalid.
     * Deactivating stops the sync scheduler from retrying until the user
     * re-authorizes (which will set isActive back to true).
     */
    private void clearTokens(EmailAccount account) {
        account.setAccessToken(null);
        account.setRefreshToken(null);
        account.setTokenExpiryDate(null);
        account.setIsActive(false);
        try {
            emailAccountRepository.save(account);
        } catch (Exception ex) {
            log.warn("Could not persist token clear for account: {}", account.getEmailAddress(), ex);
        }
    }

    /**
     * Revoke OAuth access for an account
     */
    public void revokeAccess(EmailAccount account) {
        try {
            if (account.getAccessToken() != null && !account.getAccessToken().isEmpty()) {
                Credential credential = new Credential.Builder(
                        com.google.api.client.auth.oauth2.BearerToken.authorizationHeaderAccessMethod())
                        .setTransport(httpTransport)
                        .setJsonFactory(jsonFactory)
                        .build()
                        .setAccessToken(account.getAccessToken());

                // Revoke token
                credential.getTransport().createRequestFactory()
                        .buildGetRequest(
                                new com.google.api.client.http.GenericUrl(
                                        "https://accounts.google.com/o/oauth2/revoke?token="
                                                + account.getAccessToken()))
                        .execute();
            }

            // Clear tokens from account
            account.setAccessToken(null);
            account.setRefreshToken(null);
            account.setTokenExpiryDate(null);
            account.setIsActive(false);

            emailAccountRepository.save(account);
            log.info("Revoked OAuth access for: {}", account.getEmailAddress());
        } catch (Exception e) {
            log.error("Failed to revoke access for: {}", account.getEmailAddress(), e);
        }
    }

    /**
     * Create Google Authorization Code Flow
     */
    private GoogleAuthorizationCodeFlow createFlow() throws IOException {
        if (clientId == null || clientId.isEmpty()) {
            throw new IllegalStateException(
                    "Gmail Client ID is not configured. Set GMAIL_CLIENT_ID environment variable.");
        }
        if (clientSecret == null || clientSecret.isEmpty()) {
            throw new IllegalStateException(
                    "Gmail Client Secret is not configured. Set GMAIL_CLIENT_SECRET environment variable.");
        }

        log.debug("Creating OAuth flow with clientId length: {}, clientSecret length: {}",
                clientId.length(), clientSecret.length());

        GoogleClientSecrets clientSecrets = new GoogleClientSecrets()
                .setInstalled(new GoogleClientSecrets.Details()
                        .setClientId(clientId)
                        .setClientSecret(clientSecret));

        return new GoogleAuthorizationCodeFlow.Builder(
                httpTransport,
                jsonFactory,
                clientSecrets,
                SCOPES)
                .setDataStoreFactory(new MemoryDataStoreFactory())
                .setAccessType("offline") // Request refresh token
                .build();
    }
}
