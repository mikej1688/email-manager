package com.emailmanager.entity;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
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

    // IMAP/SMTP credential storage. The API can populate this directly or via
    // appPassword.
    @JsonAlias("appPassword")
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Column(length = 500)
    private String encryptedPassword;

    private String imapServer;
    private Integer imapPort;
    private String smtpServer;
    private Integer smtpPort;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private User owner;

    @Column(nullable = false)
    private Boolean isActive = true;

    private LocalDateTime lastSyncTime;

    @Column(nullable = false)
    private Boolean initialSyncComplete = false;

    /**
     * Stores the Gmail API page token for the background pagination task.
     * Non-null means there are still historical Gmail pages yet to be fetched.
     * Cleared (set to null) once the full mailbox history has been loaded.
     */
    @Column(length = 500)
    private String gmailBackgroundPageToken;

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

    @Transient
    @JsonProperty(value = "appPassword", access = JsonProperty.Access.WRITE_ONLY)
    public String getAppPassword() {
        return encryptedPassword;
    }

    @JsonProperty("appPassword")
    public void setAppPassword(String appPassword) {
        if (appPassword != null && !appPassword.isBlank()) {
            this.encryptedPassword = appPassword;
        }
    }

    public enum EmailProvider {
        GMAIL,
        YAHOO,
        OUTLOOK,
        IMAP_GENERIC
    }
}
