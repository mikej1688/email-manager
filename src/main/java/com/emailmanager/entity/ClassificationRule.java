package com.emailmanager.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * Entity representing email classification rules
 */
@Entity
@Table(name = "classification_rules")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClassificationRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RuleType ruleType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MatchCondition matchCondition = MatchCondition.ALL;

    // Rule conditions (JSON format)
    @Column(columnDefinition = "TEXT")
    private String conditions;

    // Actions to take when rule matches (JSON format)
    @Column(columnDefinition = "TEXT")
    private String actions;

    private Integer priority; // Higher number = higher priority

    @Column(nullable = false)
    private Boolean isActive = true;

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

    public enum RuleType {
        IMPORTANCE, // Set importance level
        SPAM_DETECTION, // Mark as spam
        PHISHING_DETECTION, // Mark as phishing
        CATEGORIZATION, // Assign category
        FOLDER_MOVE, // Move to folder
        NOTIFICATION // Send notification
    }

    public enum MatchCondition {
        ALL, // All conditions must match
        ANY // Any condition can match
    }
}
