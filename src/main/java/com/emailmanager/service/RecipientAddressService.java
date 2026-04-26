package com.emailmanager.service;

import com.emailmanager.entity.RecipientAddress;
import com.emailmanager.repository.RecipientAddressRepository;
import com.emailmanager.service.email.RecipientListUtils;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class RecipientAddressService {

    private final RecipientAddressRepository recipientAddressRepository;

    @Transactional
    public void recordRecipients(String... recipientLists) {
        recordRecipients(List.of(recipientLists));
    }

    @Transactional
    public void recordRecipients(Collection<String> recipientLists) {
        LinkedHashSet<String> parsedAddresses = new LinkedHashSet<>();
        for (String recipientList : recipientLists) {
            parsedAddresses.addAll(parseRecipientList(recipientList));
        }

        LocalDateTime now = LocalDateTime.now();
        for (String emailAddress : parsedAddresses) {
            RecipientAddress recipientAddress = recipientAddressRepository.findByEmailAddress(emailAddress)
                    .orElseGet(() -> {
                        RecipientAddress created = new RecipientAddress();
                        created.setEmailAddress(emailAddress);
                        created.setUseCount(0);
                        return created;
                    });
            recipientAddress.setLastUsedAt(now);
            recipientAddress.setUseCount(recipientAddress.getUseCount() + 1);
            recipientAddressRepository.save(recipientAddress);
        }
    }

    @Transactional(readOnly = true)
    public List<String> getSuggestions(String query, int limit) {
        PageRequest pageRequest = PageRequest.of(0, Math.max(1, Math.min(limit, 20)));
        List<RecipientAddress> matches = (query == null || query.isBlank())
                ? recipientAddressRepository.findAllByOrderByLastUsedAtDescUseCountDesc(pageRequest)
                : recipientAddressRepository.findByEmailAddressContainingIgnoreCaseOrderByLastUsedAtDescUseCountDesc(
                        normalizeEmail(query), pageRequest);

        return matches.stream()
                .map(RecipientAddress::getEmailAddress)
                .toList();
    }

    private List<String> parseRecipientList(String recipientList) {
        if (recipientList == null || recipientList.isBlank()) {
            return List.of();
        }

        return RecipientListUtils.splitRecipientList(recipientList).stream()
                .map(this::extractAddress)
                .map(this::normalizeEmail)
                .filter(email -> !email.isBlank())
                .distinct()
                .toList();
    }

    private String extractAddress(String recipient) {
        try {
            InternetAddress[] parsed = InternetAddress.parse(recipient, false);
            if (parsed.length > 0 && parsed[0].getAddress() != null) {
                return parsed[0].getAddress();
            }
        } catch (AddressException ignored) {
        }

        return recipient;
    }

    private String normalizeEmail(String value) {
        if (value == null) {
            return "";
        }

        String trimmed = value.trim();
        int start = trimmed.indexOf('<');
        int end = trimmed.indexOf('>');
        if (start >= 0 && end > start) {
            trimmed = trimmed.substring(start + 1, end).trim();
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }
}