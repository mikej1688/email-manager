package com.emailmanager.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * Entity representing an email account (Gmail, Yahoo, Outlook, etc.)
 */
@Entity
@Table(name = "email_accounts")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmailAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String emailAddress;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EmailProvider provider;

    @Column(nullable = false)
    private String displayName;

    // OAuth tokens (encrypted in production)
    @Column(length = 2000)
    private String accessToken;

    @Column(length = 2000)
    private String refreshToken;

    private LocalDateTime tokenExpiryDate;

    // IMAP/SMTP credentials for non-OAuth providers
    @Column(length = 500)
    private String encryptedPassword;

    private String imapServer;
    private Integer imapPort;
    private String smtpServer;
    private Integer smtpPort;

    @Column(nullable = false)
    private Boolean isActive = true;

    private LocalDateTime lastSyncTime;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum EmailProvider {
        GMAIL,
        YAHOO,
        OUTLOOK,
        IMAP_GENERIC
    }
}
