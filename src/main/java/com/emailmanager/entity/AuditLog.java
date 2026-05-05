package com.emailmanager.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs", indexes = {
        @Index(name = "idx_audit_timestamp", columnList = "timestamp"),
        @Index(name = "idx_audit_actor", columnList = "actorEmail"),
        @Index(name = "idx_audit_resource", columnList = "resourceType,resourceId")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    // Action constants
    public static final String READ_EMAIL      = "READ_EMAIL";
    public static final String SEND_EMAIL      = "SEND_EMAIL";
    public static final String REPLY_EMAIL     = "REPLY_EMAIL";
    public static final String FORWARD_EMAIL   = "FORWARD_EMAIL";
    public static final String DELETE_EMAIL    = "DELETE_EMAIL";
    public static final String SEARCH_EMAILS   = "SEARCH_EMAILS";
    public static final String SYNC_ACCOUNT    = "SYNC_ACCOUNT";
    public static final String FULL_RESYNC     = "FULL_RESYNC";

    @Column(nullable = false, length = 50)
    private String action;

    // USER for requests triggered by a human; SYSTEM for automated jobs
    @Column(nullable = false, length = 20)
    private String actorType;

    private Long actorId;

    @Column(length = 255)
    private String actorEmail;

    @Column(length = 50)
    private String resourceType; // EMAIL | ACCOUNT

    private Long resourceId;

    @Column(length = 100)
    private String ipAddress;

    @Column(length = 500)
    private String details;
}
