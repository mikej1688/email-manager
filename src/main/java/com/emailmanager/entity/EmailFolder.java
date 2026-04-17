package com.emailmanager.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * Entity representing custom folders for organizing emails
 */
@Entity
@Table(name = "email_folders")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmailFolder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private EmailAccount account;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(nullable = false)
    private Boolean isSystemFolder = false;

    private String folderPath; // For hierarchical folders

    private Integer displayOrder;

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
}
