package com.emailmanager.repository;

import com.emailmanager.entity.EmailAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EmailAttachmentRepository extends JpaRepository<EmailAttachment, Long> {

    @Query("SELECT a FROM EmailAttachment a WHERE a.email.id = :emailId")
    List<EmailAttachment> findByEmailId(@Param("emailId") Long emailId);

    @Query("SELECT a FROM EmailAttachment a WHERE a.id = :id AND a.email.id = :emailId")
    Optional<EmailAttachment> findByIdAndEmailId(@Param("id") Long id, @Param("emailId") Long emailId);
}
