package com.emailmanager.service.email;

import com.emailmanager.entity.Email;
import com.emailmanager.entity.EmailAccount;
import java.util.List;

/**
 * Interface for email provider integrations
 */
public interface EmailProviderService {

    /**
     * Authenticate and connect to the email account
     */
    boolean authenticate(EmailAccount account);

    /**
     * Fetch new emails from the account
     */
    List<Email> fetchNewEmails(EmailAccount account, int limit);

    /**
     * Mark an email as read
     */
    boolean markAsRead(EmailAccount account, String messageId);

    /**
     * Mark an email as unread
     */
    boolean markAsUnread(EmailAccount account, String messageId);

    /**
     * Move email to a specific folder
     */
    boolean moveToFolder(EmailAccount account, String messageId, String folderName);

    /**
     * Delete an email
     */
    boolean deleteEmail(EmailAccount account, String messageId);

    /**
     * Send a notification email
     */
    boolean sendEmail(EmailAccount account, String to, String subject, String body);

    /**
     * Check connection status
     */
    boolean isConnected(EmailAccount account);
}
