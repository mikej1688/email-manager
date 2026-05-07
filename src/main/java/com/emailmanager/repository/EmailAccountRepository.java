package com.emailmanager.repository;

import com.emailmanager.entity.EmailAccount;
import com.emailmanager.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface EmailAccountRepository extends JpaRepository<EmailAccount, Long> {

    Optional<EmailAccount> findByEmailAddress(String emailAddress);

    // Background sync iterates all active accounts regardless of owner
    List<EmailAccount> findByIsActiveTrue();

    List<EmailAccount> findByProvider(EmailAccount.EmailProvider provider);

    // Owner-scoped queries used by API controllers
    List<EmailAccount> findByOwner(User owner);
    List<EmailAccount> findByOwnerAndIsActiveTrue(User owner);

    // Migration: find accounts not yet assigned to any user
    List<EmailAccount> findByOwnerIsNull();
}
