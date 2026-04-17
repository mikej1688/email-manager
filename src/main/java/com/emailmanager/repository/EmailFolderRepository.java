package com.emailmanager.repository;

import com.emailmanager.entity.EmailAccount;
import com.emailmanager.entity.EmailFolder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface EmailFolderRepository extends JpaRepository<EmailFolder, Long> {

    List<EmailFolder> findByAccount(EmailAccount account);

    Optional<EmailFolder> findByAccountAndName(EmailAccount account, String name);

    List<EmailFolder> findByAccountOrderByDisplayOrder(EmailAccount account);
}
