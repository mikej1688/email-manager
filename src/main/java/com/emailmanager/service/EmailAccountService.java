package com.emailmanager.service;

import com.emailmanager.entity.EmailAccount;
import com.emailmanager.entity.User;
import com.emailmanager.repository.EmailAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class EmailAccountService {

    private static final String YAHOO_IMAP_SERVER = "imap.mail.yahoo.com";
    private static final int YAHOO_IMAP_PORT = 993;
    private static final String YAHOO_SMTP_SERVER = "smtp.mail.yahoo.com";
    private static final int YAHOO_SMTP_PORT = 587;

    private final EmailAccountRepository emailAccountRepository;

    @Transactional(readOnly = true)
    public List<EmailAccount> getAllAccounts() {
        return emailAccountRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<EmailAccount> getActiveAccounts() {
        return emailAccountRepository.findByIsActiveTrue();
    }

    @Transactional(readOnly = true)
    public List<EmailAccount> getAccountsForUser(User owner) {
        return emailAccountRepository.findByOwner(owner);
    }

    @Transactional(readOnly = true)
    public List<EmailAccount> getActiveAccountsForUser(User owner) {
        return emailAccountRepository.findByOwnerAndIsActiveTrue(owner);
    }

    @Transactional(readOnly = true)
    public Optional<EmailAccount> findById(Long id) {
        return emailAccountRepository.findById(id);
    }

    @Transactional
    public EmailAccount createAccount(EmailAccount account) {
        applyProviderDefaults(account);
        return emailAccountRepository.save(account);
    }

    public EmailAccount createAccountPreview(EmailAccount account) {
        applyProviderDefaults(account);
        return account;
    }

    @Transactional
    public Optional<EmailAccount> updateAccount(Long id, EmailAccount accountRequest) {
        return emailAccountRepository.findById(id)
                .map(existingAccount -> {
                    merge(existingAccount, accountRequest);
                    applyProviderDefaults(existingAccount);
                    return emailAccountRepository.save(existingAccount);
                });
    }

    @Transactional
    public boolean deleteAccount(Long id) {
        return emailAccountRepository.findById(id)
                .map(account -> {
                    emailAccountRepository.delete(account);
                    return true;
                })
                .orElse(false);
    }

    private void merge(EmailAccount target, EmailAccount source) {
        target.setEmailAddress(source.getEmailAddress());
        target.setProvider(source.getProvider());
        target.setDisplayName(source.getDisplayName());
        target.setImapServer(source.getImapServer());
        target.setImapPort(source.getImapPort());
        target.setSmtpServer(source.getSmtpServer());
        target.setSmtpPort(source.getSmtpPort());

        if (source.getIsActive() != null) {
            target.setIsActive(source.getIsActive());
        }

        if (source.getEncryptedPassword() != null && !source.getEncryptedPassword().isBlank()) {
            target.setEncryptedPassword(source.getEncryptedPassword());
        }

        if (source.getAccessToken() != null) {
            target.setAccessToken(source.getAccessToken());
        }
        if (source.getRefreshToken() != null) {
            target.setRefreshToken(source.getRefreshToken());
        }
        if (source.getTokenExpiryDate() != null) {
            target.setTokenExpiryDate(source.getTokenExpiryDate());
        }
    }

    private void applyProviderDefaults(EmailAccount account) {
        if (account.getProvider() != EmailAccount.EmailProvider.YAHOO) {
            return;
        }

        if (account.getImapServer() == null || account.getImapServer().isBlank()) {
            account.setImapServer(YAHOO_IMAP_SERVER);
        }
        if (account.getImapPort() == null || account.getImapPort() <= 0) {
            account.setImapPort(YAHOO_IMAP_PORT);
        }
        if (account.getSmtpServer() == null || account.getSmtpServer().isBlank()) {
            account.setSmtpServer(YAHOO_SMTP_SERVER);
        }
        if (account.getSmtpPort() == null || account.getSmtpPort() <= 0) {
            account.setSmtpPort(YAHOO_SMTP_PORT);
        }
    }
}