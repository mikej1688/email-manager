package com.emailmanager.service;

import com.emailmanager.entity.EmailAccount;
import com.emailmanager.repository.EmailAccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailAccountServiceTest {

    @Mock
    private EmailAccountRepository emailAccountRepository;

    @InjectMocks
    private EmailAccountService emailAccountService;

    @Test
    void updateAccountPreservesStoredPasswordWhenRequestOmitsIt() {
        EmailAccount existingAccount = new EmailAccount();
        existingAccount.setId(1L);
        existingAccount.setEmailAddress("user@yahoo.com");
        existingAccount.setDisplayName("Existing User");
        existingAccount.setProvider(EmailAccount.EmailProvider.YAHOO);
        existingAccount.setEncryptedPassword("stored-app-password");
        existingAccount.setImapServer("imap.mail.yahoo.com");
        existingAccount.setImapPort(993);
        existingAccount.setSmtpServer("smtp.mail.yahoo.com");
        existingAccount.setSmtpPort(587);
        existingAccount.setIsActive(true);

        EmailAccount updateRequest = new EmailAccount();
        updateRequest.setEmailAddress("user@yahoo.com");
        updateRequest.setDisplayName("Updated User");
        updateRequest.setProvider(EmailAccount.EmailProvider.YAHOO);
        updateRequest.setImapServer("imap.mail.yahoo.com");
        updateRequest.setImapPort(993);
        updateRequest.setSmtpServer("smtp.mail.yahoo.com");
        updateRequest.setSmtpPort(587);
        updateRequest.setIsActive(true);

        when(emailAccountRepository.findById(1L)).thenReturn(Optional.of(existingAccount));
        when(emailAccountRepository.save(existingAccount)).thenReturn(existingAccount);

        Optional<EmailAccount> result = emailAccountService.updateAccount(1L, updateRequest);

        assertTrue(result.isPresent());
        assertEquals("Updated User", result.get().getDisplayName());
        assertEquals("stored-app-password", result.get().getEncryptedPassword());
    }

    @Test
    void updateAccountReplacesStoredPasswordWhenNewPasswordProvided() {
        EmailAccount existingAccount = new EmailAccount();
        existingAccount.setId(1L);
        existingAccount.setEmailAddress("user@yahoo.com");
        existingAccount.setDisplayName("Existing User");
        existingAccount.setProvider(EmailAccount.EmailProvider.YAHOO);
        existingAccount.setEncryptedPassword("stored-app-password");
        existingAccount.setIsActive(true);

        EmailAccount updateRequest = new EmailAccount();
        updateRequest.setEmailAddress("user@yahoo.com");
        updateRequest.setDisplayName("Updated User");
        updateRequest.setProvider(EmailAccount.EmailProvider.YAHOO);
        updateRequest.setEncryptedPassword("new-app-password");
        updateRequest.setIsActive(true);

        when(emailAccountRepository.findById(1L)).thenReturn(Optional.of(existingAccount));
        when(emailAccountRepository.save(existingAccount)).thenReturn(existingAccount);

        Optional<EmailAccount> result = emailAccountService.updateAccount(1L, updateRequest);

        assertTrue(result.isPresent());
        assertEquals("new-app-password", result.get().getEncryptedPassword());
    }

    @Test
    void createAccountPreviewAppliesYahooDefaults() {
        EmailAccount draftAccount = new EmailAccount();
        draftAccount.setProvider(EmailAccount.EmailProvider.YAHOO);
        draftAccount.setEmailAddress("user@yahoo.com");
        draftAccount.setDisplayName("Yahoo User");

        EmailAccount previewAccount = emailAccountService.createAccountPreview(draftAccount);

        assertNotNull(previewAccount);
        assertEquals("imap.mail.yahoo.com", previewAccount.getImapServer());
        assertEquals(993, previewAccount.getImapPort());
        assertEquals("smtp.mail.yahoo.com", previewAccount.getSmtpServer());
        assertEquals(587, previewAccount.getSmtpPort());
    }
}