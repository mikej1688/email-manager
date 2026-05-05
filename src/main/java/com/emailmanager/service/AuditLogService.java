package com.emailmanager.service;

import com.emailmanager.entity.AuditLog;
import com.emailmanager.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logEmailRead(Long emailId, Long accountId, String ipAddress) {
        persist(AuditLog.builder()
                .action(AuditLog.READ_EMAIL)
                .actorType("USER")
                .resourceType("EMAIL")
                .resourceId(emailId)
                .ipAddress(ipAddress)
                .details("account_id=" + accountId)
                .build());
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logEmailSend(Long emailId, Long accountId, String ipAddress) {
        persist(AuditLog.builder()
                .action(AuditLog.SEND_EMAIL)
                .actorType("USER")
                .resourceType("EMAIL")
                .resourceId(emailId)
                .ipAddress(ipAddress)
                .details("account_id=" + accountId)
                .build());
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logEmailReply(Long originalEmailId, Long accountId, String ipAddress) {
        persist(AuditLog.builder()
                .action(AuditLog.REPLY_EMAIL)
                .actorType("USER")
                .resourceType("EMAIL")
                .resourceId(originalEmailId)
                .ipAddress(ipAddress)
                .details("account_id=" + accountId)
                .build());
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logEmailForward(Long originalEmailId, Long accountId, String ipAddress) {
        persist(AuditLog.builder()
                .action(AuditLog.FORWARD_EMAIL)
                .actorType("USER")
                .resourceType("EMAIL")
                .resourceId(originalEmailId)
                .ipAddress(ipAddress)
                .details("account_id=" + accountId)
                .build());
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logEmailDelete(Long emailId, Long accountId, String ipAddress) {
        persist(AuditLog.builder()
                .action(AuditLog.DELETE_EMAIL)
                .actorType("USER")
                .resourceType("EMAIL")
                .resourceId(emailId)
                .ipAddress(ipAddress)
                .details("account_id=" + accountId)
                .build());
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logEmailSearch(Long accountId, int queryLength, String ipAddress, int resultCount) {
        persist(AuditLog.builder()
                .action(AuditLog.SEARCH_EMAILS)
                .actorType("USER")
                .resourceType("ACCOUNT")
                .resourceId(accountId)
                .ipAddress(ipAddress)
                // Keyword itself is intentionally omitted — only length is stored
                .details("query_length=" + queryLength + " results=" + resultCount)
                .build());
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logSync(Long accountId, String accountEmail, int savedCount) {
        persist(AuditLog.builder()
                .action(AuditLog.SYNC_ACCOUNT)
                .actorType("SYSTEM")
                .actorEmail(accountEmail)
                .resourceType("ACCOUNT")
                .resourceId(accountId)
                .details("new_emails=" + savedCount)
                .build());
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logFullResync(Long accountId, String accountEmail) {
        persist(AuditLog.builder()
                .action(AuditLog.FULL_RESYNC)
                .actorType("SYSTEM")
                .actorEmail(accountEmail)
                .resourceType("ACCOUNT")
                .resourceId(accountId)
                .details("full resync initiated — incremental sync state cleared")
                .build());
    }

    private void persist(AuditLog entry) {
        entry.setTimestamp(LocalDateTime.now());
        try {
            auditLogRepository.save(entry);
        } catch (Exception e) {
            // Audit failures must never break the main request flow
            log.error("Failed to write audit log entry [action={}]: {}", entry.getAction(), e.getMessage());
        }
    }
}
