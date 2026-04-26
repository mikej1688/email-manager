package com.emailmanager.entity;

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

    @Column(nullable = false)
    private String subject;

    @Column(nullable = false)
    private String fromAddress;

    private String fromName;

    @Column(length = 2000)
    private String toAddresses;

    @Column(length = 2000)
    private String ccAddresses;

    @Column(columnDefinition = "TEXT")
    private String bodyPlainText;

    @Column(columnDefinition = "TEXT")
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

    private LocalDateTime processedAt;
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
