package com.emailmanager.repository;

import com.emailmanager.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    @Query("SELECT a FROM AuditLog a WHERE " +
           "(:action IS NULL OR a.action = :action) AND " +
           "(:actorEmail IS NULL OR a.actorEmail = :actorEmail) AND " +
           "(:resourceType IS NULL OR a.resourceType = :resourceType) AND " +
           "(:resourceId IS NULL OR a.resourceId = :resourceId) " +
           "ORDER BY a.timestamp DESC")
    Page<AuditLog> search(
            @Param("action") String action,
            @Param("actorEmail") String actorEmail,
            @Param("resourceType") String resourceType,
            @Param("resourceId") Long resourceId,
            Pageable pageable);
}
