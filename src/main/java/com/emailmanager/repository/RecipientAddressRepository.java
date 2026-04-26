package com.emailmanager.repository;

import com.emailmanager.entity.RecipientAddress;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RecipientAddressRepository extends JpaRepository<RecipientAddress, Long> {

    Optional<RecipientAddress> findByEmailAddress(String emailAddress);

    List<RecipientAddress> findByEmailAddressContainingIgnoreCaseOrderByLastUsedAtDescUseCountDesc(
            String emailAddress,
            Pageable pageable);

    List<RecipientAddress> findAllByOrderByLastUsedAtDescUseCountDesc(Pageable pageable);
}