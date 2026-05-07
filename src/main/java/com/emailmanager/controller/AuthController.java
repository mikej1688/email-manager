package com.emailmanager.controller;

import com.emailmanager.config.JwtUtil;
import com.emailmanager.entity.User;
import com.emailmanager.repository.UserRepository;
import com.emailmanager.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.view.RedirectView;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Google OAuth user login — distinct from /api/oauth which manages Gmail email accounts.
 *
 * Flow:
 *   GET /api/auth/google/url       → returns the Google consent URL
 *   GET /api/auth/google/callback  → exchanges code, issues JWT, redirects to frontend
 *   GET /api/auth/me               → returns current user profile
 *   GET /api/auth/users            → ADMIN: list all users
 *   PUT /api/auth/users/{id}/role  → ADMIN: change a user's role
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final UserService userService;
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final RestTemplate restTemplate;

    @Value("${gmail.client.id}")
    private String clientId;

    @Value("${gmail.client.secret}")
    private String clientSecret;

    @Value("${google.auth.redirect-uri:http://localhost:8080/api/auth/google/callback}")
    private String authRedirectUri;

    @Value("${frontend.auth.callback-url:http://localhost:3000/auth/callback}")
    private String frontendCallbackUrl;

    private static final String GOOGLE_AUTH_ENDPOINT  = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String GOOGLE_TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token";
    private static final String GOOGLE_USERINFO_URL   = "https://www.googleapis.com/oauth2/v3/userinfo";

    /** Returns the Google OAuth consent URL. Frontend redirects the browser here. */
    @GetMapping("/google/url")
    public ResponseEntity<Map<String, String>> getGoogleAuthUrl() {
        String state = UUID.randomUUID().toString();
        String url = UriComponentsBuilder.fromHttpUrl(GOOGLE_AUTH_ENDPOINT)
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", authRedirectUri)
                .queryParam("response_type", "code")
                .queryParam("scope", "openid email profile")
                .queryParam("access_type", "online")
                .queryParam("state", state)
                .toUriString();
        return ResponseEntity.ok(Map.of("url", url));
    }

    /**
     * Google redirects here after user consent.
     * Exchanges the code for a user profile, issues a JWT, then redirects the browser
     * to the React frontend which stores the token and proceeds to the app.
     */
    @GetMapping("/google/callback")
    public RedirectView handleGoogleCallback(
            @RequestParam String code,
            @RequestParam(required = false) String error) {

        RedirectView redirect = new RedirectView();

        if (error != null) {
            log.warn("Google auth denied: {}", error);
            redirect.setUrl(frontendCallbackUrl + "?error=" + error);
            return redirect;
        }

        try {
            String accessToken = exchangeCodeForToken(code);
            Map<String, Object> profile = fetchUserProfile(accessToken);

            String googleId = (String) profile.get("sub");
            String email    = (String) profile.get("email");
            String name     = (String) profile.getOrDefault("name", email);

            User user = userService.findOrCreateUser(googleId, email, name);
            String jwt = jwtUtil.generateToken(user);

            log.info("Login: {} (id={}, role={})", email, user.getId(), user.getRole());
            redirect.setUrl(frontendCallbackUrl + "?token=" + jwt);

        } catch (Exception e) {
            log.error("Google auth callback failed", e);
            redirect.setUrl(frontendCallbackUrl + "?error=authentication_failed");
        }

        return redirect;
    }

    /** Returns the authenticated user's own profile. */
    @GetMapping("/me")
    public ResponseEntity<User> getCurrentUser(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(user);
    }

    /** ADMIN only: list all registered users. */
    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<User>> listUsers() {
        return ResponseEntity.ok(userRepository.findAll());
    }

    /** ADMIN only: change a user's role. */
    @PutMapping("/users/{id}/role")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<User> updateUserRole(
            @PathVariable Long id,
            @RequestParam User.Role role) {
        return ResponseEntity.ok(userService.updateRole(id, role));
    }

    // ── private helpers ──────────────────────────────────────────────────────

    private String exchangeCodeForToken(String code) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("code",          code);
        body.add("client_id",     clientId);
        body.add("client_secret", clientSecret);
        body.add("redirect_uri",  authRedirectUri);
        body.add("grant_type",    "authorization_code");

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                GOOGLE_TOKEN_ENDPOINT, HttpMethod.POST,
                new HttpEntity<>(body, headers),
                new ParameterizedTypeReference<>() {});

        Map<String, Object> tokens = response.getBody();
        if (tokens == null || !tokens.containsKey("access_token")) {
            throw new RuntimeException("No access_token in Google token response");
        }
        return (String) tokens.get("access_token");
    }

    private Map<String, Object> fetchUserProfile(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                GOOGLE_USERINFO_URL, HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {});

        if (response.getBody() == null) {
            throw new RuntimeException("Empty response from Google userinfo endpoint");
        }
        return response.getBody();
    }
}
