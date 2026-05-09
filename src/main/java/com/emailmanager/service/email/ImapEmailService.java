package com.emailmanager.service.email;

import com.emailmanager.entity.Email;
import com.emailmanager.entity.EmailAccount;
import com.emailmanager.entity.EmailAttachment;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.search.FlagTerm;
import jakarta.mail.search.MessageIDTerm;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * IMAP/SMTP service for Yahoo, Outlook, and other email providers
 */
@Service
@Slf4j
public class ImapEmailService implements EmailProviderService {

    private static final int DEFAULT_IMAPS_PORT = 993;
    private static final String MAIL_TIMEOUT_MS = "5000";

    @Override
    public boolean authenticate(EmailAccount account) {
        try {
            Store store = openImapStore(account);
            boolean connected = store.isConnected();
            store.close();
            log.info("Successfully authenticated IMAP account: {}", account.getEmailAddress());
            return connected;
        } catch (Exception e) {
            log.error("Failed to authenticate IMAP account: {}", account.getEmailAddress(), e);
            return false;
        }
    }

    @Override
    public List<Email> fetchNewEmails(EmailAccount account, int limit) {
        List<Email> emails = new ArrayList<>();
        Store store = null;
        Folder inbox = null;

        try {
            if (Thread.currentThread().isInterrupted()) {
                log.info("Skipping IMAP fetch for {} because the sync thread was interrupted",
                        account.getEmailAddress());
                return emails;
            }

            store = openImapStore(account);

            inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_ONLY);

            // Get unread messages
            Message[] messages = inbox.search(new FlagTerm(new Flags(Flags.Flag.SEEN), false));

            int count = Math.min(messages.length, limit);
            for (int i = messages.length - count; i < messages.length; i++) {
                if (Thread.currentThread().isInterrupted()) {
                    log.info("Stopping IMAP fetch loop for {} because the sync thread was interrupted",
                            account.getEmailAddress());
                    break;
                }

                Message message = messages[i];
                Email email = convertImapToEmail(message, account);
                emails.add(email);
            }

            log.info("Fetched {} new emails from IMAP account: {}", emails.size(), account.getEmailAddress());
        } catch (Exception e) {
            log.error("Failed to fetch emails from IMAP account: {}", account.getEmailAddress(), e);
        } finally {
            closeQuietly(inbox, store);
        }

        return emails;
    }

    @Override
    public boolean markAsRead(EmailAccount account, String messageId) {
        Store store = null;
        Folder inbox = null;

        try {
            store = openImapStore(account);

            inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_WRITE);

            Message[] messages = inbox.search(new MessageIDTerm(messageId));
            if (messages.length > 0) {
                messages[0].setFlag(Flags.Flag.SEEN, true);
                return true;
            }
        } catch (Exception e) {
            log.error("Failed to mark email as read", e);
        } finally {
            closeQuietly(inbox, store);
        }

        return false;
    }

    @Override
    public boolean markAsUnread(EmailAccount account, String messageId) {
        Store store = null;
        Folder inbox = null;

        try {
            store = openImapStore(account);

            inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_WRITE);

            Message[] messages = inbox.search(new MessageIDTerm(messageId));
            if (messages.length > 0) {
                messages[0].setFlag(Flags.Flag.SEEN, false);
                return true;
            }
        } catch (Exception e) {
            log.error("Failed to mark email as unread", e);
        } finally {
            closeQuietly(inbox, store);
        }

        return false;
    }

    @Override
    public boolean moveToFolder(EmailAccount account, String messageId, String folderName) {
        Store store = null;
        Folder inbox = null;
        Folder targetFolder = null;

        try {
            store = openImapStore(account);

            inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_WRITE);

            targetFolder = store.getFolder(folderName);
            if (!targetFolder.exists()) {
                targetFolder.create(Folder.HOLDS_MESSAGES);
            }
            targetFolder.open(Folder.READ_WRITE);

            Message[] messages = inbox.search(new MessageIDTerm(messageId));
            if (messages.length > 0) {
                inbox.copyMessages(messages, targetFolder);
                messages[0].setFlag(Flags.Flag.DELETED, true);
                inbox.expunge();
                return true;
            }
        } catch (Exception e) {
            log.error("Failed to move email to folder", e);
        } finally {
            closeQuietly(targetFolder);
            closeQuietly(inbox, store);
        }

        return false;
    }

    @Override
    public boolean deleteEmail(EmailAccount account, String messageId) {
        Store store = null;
        Folder folder = null;

        try {
            store = openImapStore(account);

            Folder[] folders = store.getDefaultFolder().list("*");
            for (Folder candidate : folders) {
                if ((candidate.getType() & Folder.HOLDS_MESSAGES) == 0 || !candidate.exists()) {
                    continue;
                }

                try {
                    candidate.open(Folder.READ_WRITE);
                    Message[] messages = candidate.search(new MessageIDTerm(messageId));
                    if (messages.length > 0) {
                        messages[0].setFlag(Flags.Flag.DELETED, true);
                        candidate.expunge();
                        return true;
                    }
                } finally {
                    closeQuietly(candidate);
                }
            }
        } catch (Exception e) {
            log.error("Failed to delete email", e);
        } finally {
            closeQuietly(folder, store);
        }

        return false;
    }

    @Override
    public boolean sendEmail(EmailAccount account, String to, String subject, String body) {
        try {
            Properties props = new Properties();
            props.put("mail.smtp.host", account.getSmtpServer());
            props.put("mail.smtp.port", account.getSmtpPort());
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", "true");

            Session session = Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(
                            account.getEmailAddress(),
                            account.getAppPassword());
                }
            });

            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(account.getEmailAddress()));
            message.setRecipients(MimeMessage.RecipientType.TO, RecipientListUtils.toInternetAddresses(to));
            message.setSubject(subject);
            message.setText(body);

            Transport.send(message);
            log.info("Sent email notification to: {}", to);
            return true;
        } catch (Exception e) {
            log.error("Failed to send email", e);
            return false;
        }
    }

    @Override
    public boolean isConnected(EmailAccount account) {
        return authenticate(account);
    }

    private Store openImapStore(EmailAccount account) throws MessagingException {
        int imapPort = resolveImapPort(account);

        Properties props = new Properties();
        props.put("mail.store.protocol", "imaps");
        props.put("mail.imaps.host", account.getImapServer());
        props.put("mail.imaps.port", String.valueOf(imapPort));
        props.put("mail.imaps.ssl.enable", "true");
        props.put("mail.imap.connectiontimeout", MAIL_TIMEOUT_MS);
        props.put("mail.imap.timeout", MAIL_TIMEOUT_MS);
        props.put("mail.imap.writetimeout", MAIL_TIMEOUT_MS);
        props.put("mail.imaps.connectiontimeout", MAIL_TIMEOUT_MS);
        props.put("mail.imaps.timeout", MAIL_TIMEOUT_MS);
        props.put("mail.imaps.writetimeout", MAIL_TIMEOUT_MS);

        Session session = Session.getInstance(props);
        Store store = session.getStore("imaps");
        store.connect(
                account.getImapServer(),
                imapPort,
                account.getEmailAddress(),
                account.getEncryptedPassword());

        return store;
    }

    private int resolveImapPort(EmailAccount account) {
        if (account.getImapPort() != null && account.getImapPort() > 0) {
            return account.getImapPort();
        }

        if (account.getProvider() == EmailAccount.EmailProvider.YAHOO) {
            log.warn("IMAP port missing for Yahoo account {}. Falling back to {}.",
                    account.getEmailAddress(), DEFAULT_IMAPS_PORT);
        }

        return DEFAULT_IMAPS_PORT;
    }

    private Email convertImapToEmail(Message message, EmailAccount account) throws MessagingException {
        Email email = new Email();
        email.setAccount(account);

        // Message ID
        String[] messageIds = message.getHeader("Message-ID");
        if (messageIds != null && messageIds.length > 0) {
            email.setMessageId(messageIds[0]);
        }

        // Subject
        email.setSubject(message.getSubject());

        // From
        Address[] fromAddresses = message.getFrom();
        if (fromAddresses != null && fromAddresses.length > 0) {
            InternetAddress from = (InternetAddress) fromAddresses[0];
            email.setFromAddress(from.getAddress());
            email.setFromName(from.getPersonal());
        }

        // To
        Address[] toAddresses = message.getRecipients(Message.RecipientType.TO);
        if (toAddresses != null) {
            email.setToAddresses(InternetAddress.toString(toAddresses));
        }

        // CC
        Address[] ccAddresses = message.getRecipients(Message.RecipientType.CC);
        if (ccAddresses != null) {
            email.setCcAddresses(InternetAddress.toString(ccAddresses));
        }

        // Body and attachments
        try {
            Object content = message.getContent();
            if (content instanceof String) {
                email.setBodyPlainText((String) content);
            } else if (content instanceof Multipart) {
                extractMultipart((Multipart) content, email);
            }
        } catch (Exception e) {
            log.warn("Failed to extract email body", e);
        }

        // Date
        if (message.getReceivedDate() != null) {
            email.setReceivedDate(
                    LocalDateTime.ofInstant(
                            message.getReceivedDate().toInstant(),
                            ZoneId.systemDefault()));
        }

        // Read status
        email.setIsRead(message.isSet(Flags.Flag.SEEN));

        return email;
    }

    private void extractMultipart(Multipart multipart, Email email) throws Exception {
        for (int i = 0; i < multipart.getCount(); i++) {
            BodyPart part = multipart.getBodyPart(i);
            String disposition = part.getDisposition();
            if (part.isMimeType("text/plain") && !Part.ATTACHMENT.equalsIgnoreCase(disposition)) {
                if (email.getBodyPlainText() == null) {
                    email.setBodyPlainText(part.getContent().toString());
                }
            } else if (part.isMimeType("text/html") && !Part.ATTACHMENT.equalsIgnoreCase(disposition)) {
                if (email.getBodyHtml() == null) {
                    email.setBodyHtml(part.getContent().toString());
                }
            } else if (part.getContent() instanceof Multipart) {
                extractMultipart((Multipart) part.getContent(), email);
            } else if (Part.ATTACHMENT.equalsIgnoreCase(disposition) || part.getFileName() != null) {
                String filename = part.getFileName();
                if (filename == null) filename = "attachment-" + i;
                String contentType = part.getContentType();
                if (contentType != null && contentType.contains(";")) {
                    contentType = contentType.substring(0, contentType.indexOf(';')).trim();
                }
                byte[] data = null;
                try (InputStream is = part.getInputStream()) {
                    data = is.readAllBytes();
                } catch (Exception e) {
                    log.warn("Failed to read attachment data for '{}': {}", filename, e.getMessage());
                }
                EmailAttachment attachment = new EmailAttachment();
                attachment.setEmail(email);
                attachment.setFilename(filename);
                attachment.setContentType(contentType != null ? contentType : "application/octet-stream");
                attachment.setData(data);
                attachment.setSize(data != null ? (long) data.length : 0L);
                email.getAttachments().add(attachment);
            }
        }
        if (!email.getAttachments().isEmpty()) {
            email.setHasAttachments(true);
        }
    }

    private void closeQuietly(Folder folder) {
        if (folder != null && folder.isOpen()) {
            try {
                folder.close(false);
            } catch (Exception e) {
                log.warn("Failed to close folder", e);
            }
        }
    }

    private void closeQuietly(Folder folder, Store store) {
        closeQuietly(folder);
        if (store != null && store.isConnected()) {
            try {
                store.close();
            } catch (Exception e) {
                log.warn("Failed to close store", e);
            }
        }
    }
}
