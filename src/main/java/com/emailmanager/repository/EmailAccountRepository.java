package com.emailmanager.repository;

import com.emailmanager.entity.EmailAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface EmailAccountRepository extends JpaRepository<EmailAccount, Long> {

    Optional<EmailAccount> findByEmailAddress(String emailAddress);

    List<EmailAccount> findByIsActiveTrue();

    List<EmailAccount> findByProvider(EmailAccount.EmailProvider provider);
}
