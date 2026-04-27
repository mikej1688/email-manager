package com.emailmanager.repository;

import com.emailmanager.entity.Email;
import com.emailmanager.entity.EmailAccount;
import com.emailmanager.entity.EmailFolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface EmailRepository extends JpaRepository<Email, Long> {

        Optional<Email> findByMessageId(String messageId);

        Page<Email> findByAccount(EmailAccount account, Pageable pageable);

        Page<Email> findByAccountAndCategoryNotIn(EmailAccount account, List<Email.EmailCategory> excludedCategories,
                        Pageable pageable);

        Page<Email> findByAccountAndIsRead(EmailAccount account, Boolean isRead, Pageable pageable);

        Page<Email> findByAccountAndIsReadAndCategoryNotIn(EmailAccount account, Boolean isRead,
                        List<Email.EmailCategory> excludedCategories, Pageable pageable);

        Page<Email> findByAccountAndCategoryAndFolderIsNull(EmailAccount account, Email.EmailCategory category,
                        Pageable pageable);

        Page<Email> findByAccountAndFolder(EmailAccount account, EmailFolder folder, Pageable pageable);

        Page<Email> findByAccountAndImportance(EmailAccount account, Email.ImportanceLevel importance,
                        Pageable pageable);

        List<Email> findByAccountAndIsReadFalseAndRequiresActionTrue(EmailAccount account);

        List<Email> findByDueDateBeforeAndUserNotifiedFalse(LocalDateTime date);

        List<Email> findByIsSpamTrueAndProcessedAtIsNull();

        List<Email> findByIsPhishingTrueAndUserNotifiedFalse();

        @Query("SELECT e FROM Email e WHERE e.account = :account AND e.isRead = false ORDER BY e.importance DESC, e.receivedDate DESC")
        List<Email> findUnreadEmailsByImportance(@Param("account") EmailAccount account);

        Long countByAccountAndIsReadFalse(EmailAccount account);

        @Query("SELECT e FROM Email e WHERE e.account = :account AND (" +
                        "LOWER(e.subject) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
                        "LOWER(e.fromAddress) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
                        "LOWER(e.toAddresses) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
                        "LOWER(e.bodyPlainText) LIKE LOWER(CONCAT('%', :query, '%'))" +
                        ") ORDER BY e.receivedDate DESC")
        Page<Email> searchByAccount(@Param("account") EmailAccount account, @Param("query") String query,
                        Pageable pageable);
}
