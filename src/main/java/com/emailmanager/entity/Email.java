package com.emailmanager.entity;

import com.emailmanager.config.AttributeEncryptor;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * Entity representing an individual email message
 */
@Entity
@Table(name = "emails", indexes = {
        @Index(name = "idx_account_id", columnList = "account_id"),
        @Index(name = "idx_received_date", columnList = "receivedDate"),
        @Index(name = "idx_is_read", columnList = "isRead"),
        @Index(name = "idx_importance", columnList = "importance")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Email {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private EmailAccount account;

    @Column(nullable = false, unique = true)
    private String messageId; // Unique ID from email provider

    @Convert(converter = AttributeEncryptor.class)
    @Column(nullable = false, columnDefinition = "TEXT")
    private String subject;

    @Convert(converter = AttributeEncryptor.class)
    @Column(nullable = false, columnDefinition = "TEXT")
    private String fromAddress;

    @Convert(converter = AttributeEncryptor.class)
    @Column(columnDefinition = "TEXT")
    private String fromName;

    @Convert(converter = AttributeEncryptor.class)
    @Column(columnDefinition = "TEXT")
    private String toAddresses;

    @Column(columnDefinition = "TEXT")
    private String ccAddresses;

    @Convert(converter = AttributeEncryptor.class)
    @Column(columnDefinition = "LONGTEXT")
    private String bodyPlainText;

    @Convert(converter = AttributeEncryptor.class)
    @Column(columnDefinition = "LONGTEXT")
    private String bodyHtml;

    private LocalDateTime receivedDate;

    @Column(nullable = false)
    private Boolean isRead = false;

    @Column(nullable = false)
    private Boolean isStarred = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ImportanceLevel importance = ImportanceLevel.NORMAL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EmailCategory category = EmailCategory.INBOX;

    @Column(nullable = false)
    private Boolean isSpam = false;

    @Column(nullable = false)
    private Boolean isPhishing = false;

    // AI/Rule-based classification scores
    private Double importanceScore;
    private Double spamScore;
    private Double phishingScore;

    @Column(length = 1000)
    private String classificationReason;

    // Deadline/Due date extracted from email content
    private LocalDateTime dueDate;

    @Column(nullable = false)
    private Boolean requiresAction = false;

    @Column(nullable = false)
    private Boolean userNotified = false;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "folder_id")
    private EmailFolder folder;

    // Comma-padded list of raw Gmail label IDs, e.g. ",INBOX,CATEGORY_PROMOTIONS,UNREAD,"
    // Used to query emails by label for accurate Gmail-side counts.
    @Column(columnDefinition = "TEXT")
    private String gmailLabelIds;

    private LocalDateTime processedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Truncation limits prevent MySQL max_allowed_packet errors from huge HTML newsletters.
    // 500 KB plain / 1 MB HTML keeps the encrypted+Base64 INSERT packet well under 4 MB.
    private static final int MAX_PLAIN_TEXT_LENGTH = 500_000;
    private static final int MAX_HTML_LENGTH = 1_000_000;

    public void setBodyPlainText(String bodyPlainText) {
        this.bodyPlainText = bodyPlainText != null && bodyPlainText.length() > MAX_PLAIN_TEXT_LENGTH
                ? bodyPlainText.substring(0, MAX_PLAIN_TEXT_LENGTH)
                : bodyPlainText;
    }

    public void setBodyHtml(String bodyHtml) {
        this.bodyHtml = bodyHtml != null && bodyHtml.length() > MAX_HTML_LENGTH
                ? bodyHtml.substring(0, MAX_HTML_LENGTH)
                : bodyHtml;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum ImportanceLevel {
        URGENT, // Requires immediate attention
        HIGH, // Important, handle soon
        NORMAL, // Standard email
        LOW // Can be handled later
    }

    public enum EmailCategory {
        INBOX,
        IMPORTANT,
        SOCIAL,
        PROMOTIONS,
        UPDATES,
        FORUMS,
        SPAM,
        TRASH,
        ARCHIVED,
        SENT,
        DRAFT
    }
}
