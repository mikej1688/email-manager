package com.emailmanager.service.email;

import com.emailmanager.entity.Email;
import com.emailmanager.entity.EmailAccount;
import com.emailmanager.service.OAuth2Service;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Gmail API integration service
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class GmailService implements EmailProviderService {

    private static final String APPLICATION_NAME = "Email Manager";
    private final OAuth2Service oAuth2Service;

    @Override
    public boolean authenticate(EmailAccount account) {
        try {
            Gmail service = getGmailService(account);
            // Test connection by getting user profile
            service.users().getProfile("me").execute();
            log.info("Successfully authenticated Gmail account: {}", account.getEmailAddress());
            return true;
        } catch (Exception e) {
            log.error("Failed to authenticate Gmail account: {}", account.getEmailAddress(), e);
            return false;
        }
    }

    @Override
    public List<Email> fetchNewEmails(EmailAccount account, int limit) {
        List<Email> emails = new ArrayList<>();
        try {
            Gmail service = getGmailService(account);

            // List messages
            com.google.api.services.gmail.Gmail.Users.Messages.List request = service.users().messages().list("me")
                    .setQ("is:unread")
                    .setMaxResults((long) limit);

            List<Message> messages = request.execute().getMessages();

            if (messages != null && !messages.isEmpty()) {
                for (Message message : messages) {
                    Message fullMessage = service.users().messages()
                            .get("me", message.getId())
                            .setFormat("full")
                            .execute();

                    Email email = convertGmailToEmail(fullMessage, account, service);
                    emails.add(email);
                }
            }

            log.info("Fetched {} new emails from Gmail account: {}", emails.size(), account.getEmailAddress());
        } catch (Exception e) {
            log.error("Failed to fetch emails from Gmail account: {}", account.getEmailAddress(), e);
        }
        return emails;
    }

    @Override
    public boolean markAsRead(EmailAccount account, String messageId) {
        try {
            Gmail service = getGmailService(account);
            com.google.api.services.gmail.model.ModifyMessageRequest request = new com.google.api.services.gmail.model.ModifyMessageRequest()
                    .setRemoveLabelIds(List.of("UNREAD"));

            service.users().messages().modify("me", messageId, request).execute();
            return true;
        } catch (Exception e) {
            log.error("Failed to mark email as read", e);
            return false;
        }
    }

    @Override
    public boolean markAsUnread(EmailAccount account, String messageId) {
        try {
            Gmail service = getGmailService(account);
            com.google.api.services.gmail.model.ModifyMessageRequest request = new com.google.api.services.gmail.model.ModifyMessageRequest()
                    .setAddLabelIds(List.of("UNREAD"));

            service.users().messages().modify("me", messageId, request).execute();
            return true;
        } catch (Exception e) {
            log.error("Failed to mark email as unread", e);
            return false;
        }
    }

    @Override
    public boolean moveToFolder(EmailAccount account, String messageId, String folderName) {
        try {
            Gmail service = getGmailService(account);
            // In Gmail, folders are labels
            com.google.api.services.gmail.model.ModifyMessageRequest request = new com.google.api.services.gmail.model.ModifyMessageRequest()
                    .setAddLabelIds(List.of(folderName))
                    .setRemoveLabelIds(List.of("INBOX"));

            service.users().messages().modify("me", messageId, request).execute();
            return true;
        } catch (Exception e) {
            log.error("Failed to move email to folder", e);
            return false;
        }
    }

    @Override
    public boolean deleteEmail(EmailAccount account, String messageId) {
        try {
            Gmail service = getGmailService(account);
            service.users().messages().trash("me", messageId).execute();
            return true;
        } catch (Exception e) {
            log.error("Failed to delete email", e);
            return false;
        }
    }

    @Override
    public boolean sendEmail(EmailAccount account, String to, String subject, String body) {
        try {
            Gmail service = getGmailService(account);
            // Implementation for sending email
            // This requires constructing a MIME message
            log.info("Sending email notification to: {}", to);
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

    private Gmail getGmailService(EmailAccount account) {
        try {
            // Get credential with automatic token refresh
            Credential credential = oAuth2Service.getCredential(account);

            return new Gmail.Builder(
                    new NetHttpTransport(),
                    GsonFactory.getDefaultInstance(),
                    credential)
                    .setApplicationName(APPLICATION_NAME)
                    .build();
        } catch (Exception e) {
            log.error("Failed to create Gmail service for account: {}", account.getEmailAddress(), e);
            throw new RuntimeException("Failed to create Gmail service. Please re-authenticate your account.", e);
        }
    }

    /**
     * Re-fetches the body of an already-saved email from Gmail and embeds inline
     * images.
     * Call this to fix emails that were saved before the image-handling fix.
     */
    public void refreshEmailBody(EmailAccount account, Email email) throws Exception {
        Gmail service = getGmailService(account);
        Message fullMessage = service.users().messages()
                .get("me", email.getMessageId())
                .setFormat("full")
                .execute();
        email.setBodyHtml(null);
        email.setBodyPlainText(null);
        Map<String, String> cidImages = new LinkedHashMap<>();
        List<String> standaloneImages = new ArrayList<>();
        if (fullMessage.getPayload() != null) {
            extractBody(fullMessage.getPayload(), email, service, email.getMessageId(), cidImages, standaloneImages);
        }
        applyCidAndImageFallback(email, cidImages, standaloneImages);
    }

    private Email convertGmailToEmail(Message gmailMessage, EmailAccount account, Gmail gmailService) {
        Email email = new Email();
        email.setAccount(account);
        email.setMessageId(gmailMessage.getId());

        // Parse headers
        if (gmailMessage.getPayload() != null && gmailMessage.getPayload().getHeaders() != null) {
            gmailMessage.getPayload().getHeaders().forEach(header -> {
                String name = header.getName();
                String value = header.getValue();

                switch (name.toLowerCase()) {
                    case "subject":
                        email.setSubject(value);
                        break;
                    case "from":
                        email.setFromAddress(extractEmail(value));
                        email.setFromName(extractName(value));
                        break;
                    case "to":
                        email.setToAddresses(value);
                        break;
                    case "cc":
                        email.setCcAddresses(value);
                        break;
                    case "date":
                        // Parse date
                        break;
                }
            });
        }

        // Parse body — handle both simple and multipart MIME structures
        Map<String, String> cidImages = new LinkedHashMap<>();
        List<String> standaloneImages = new ArrayList<>();
        if (gmailMessage.getPayload() != null) {
            extractBody(gmailMessage.getPayload(), email, gmailService, gmailMessage.getId(), cidImages,
                    standaloneImages);
        }
        applyCidAndImageFallback(email, cidImages, standaloneImages);

        // Set dates
        if (gmailMessage.getInternalDate() != null) {
            email.setReceivedDate(new java.sql.Timestamp(gmailMessage.getInternalDate()).toLocalDateTime());
        }

        // Check if unread
        email.setIsRead(!gmailMessage.getLabelIds().contains("UNREAD"));

        return email;
    }

    /**
     * Recursively extracts text, HTML, and inline images from a MIME message part.
     * Inline images (with Content-ID) are collected as base64 data URIs so that
     * cid: references in the HTML can be rewritten to data URIs before saving.
     */
    private void extractBody(com.google.api.services.gmail.model.MessagePart part, Email email,
            Gmail gmailService, String messageId,
            Map<String, String> cidImages, List<String> standaloneImages) {
        String mimeType = part.getMimeType() != null ? part.getMimeType().toLowerCase() : "";

        if (mimeType.equals("text/html")) {
            String data = part.getBody() != null ? part.getBody().getData() : null;
            if (data != null) {
                email.setBodyHtml(new String(Base64.getUrlDecoder().decode(data)));
            }
        } else if (mimeType.equals("text/plain")) {
            String data = part.getBody() != null ? part.getBody().getData() : null;
            if (data != null && email.getBodyPlainText() == null) {
                email.setBodyPlainText(new String(Base64.getUrlDecoder().decode(data)));
            }
        } else if (mimeType.startsWith("multipart/")) {
            if (part.getParts() != null) {
                for (com.google.api.services.gmail.model.MessagePart subPart : part.getParts()) {
                    extractBody(subPart, email, gmailService, messageId, cidImages, standaloneImages);
                }
            }
        } else if (mimeType.startsWith("image/")) {
            // Collect inline images; replace cid: refs after full traversal
            String contentId = null;
            if (part.getHeaders() != null) {
                for (com.google.api.services.gmail.model.MessagePartHeader h : part.getHeaders()) {
                    if ("content-id".equalsIgnoreCase(h.getName())) {
                        contentId = h.getValue().replaceAll("[<>\\s]", "");
                        break;
                    }
                }
            }
            String base64Data = null;
            if (part.getBody() != null) {
                if (part.getBody().getData() != null) {
                    // URL-safe base64 → standard base64 for data URI
                    base64Data = part.getBody().getData().replace('-', '+').replace('_', '/');
                } else if (part.getBody().getAttachmentId() != null && gmailService != null) {
                    try {
                        com.google.api.services.gmail.model.MessagePartBody attachment = gmailService.users().messages()
                                .attachments()
                                .get("me", messageId, part.getBody().getAttachmentId())
                                .execute();
                        if (attachment.getData() != null) {
                            base64Data = attachment.getData().replace('-', '+').replace('_', '/');
                        }
                    } catch (Exception e) {
                        log.warn("Failed to fetch inline image attachment (cid={}): {}", contentId, e.getMessage());
                    }
                }
            }
            if (base64Data != null) {
                String dataUri = "data:" + mimeType + ";base64," + base64Data;
                if (contentId != null) {
                    cidImages.put(contentId, dataUri);
                } else {
                    standaloneImages.add(dataUri);
                }
            }
        } else if (mimeType.isEmpty()) {
            // Simple body with no explicit mimeType — treat as plain text
            String data = part.getBody() != null ? part.getBody().getData() : null;
            if (data != null && email.getBodyPlainText() == null) {
                email.setBodyPlainText(new String(Base64.getUrlDecoder().decode(data)));
            }
        }
        // Other types (application/pdf, etc.) are intentionally skipped
    }

    /**
     * After extractBody traversal: replace cid: refs in HTML with data URIs,
     * and for image-only emails generate an HTML body from the collected images.
     */
    private void applyCidAndImageFallback(Email email, Map<String, String> cidImages,
            List<String> standaloneImages) {
        if (email.getBodyHtml() != null && !cidImages.isEmpty()) {
            String html = email.getBodyHtml();
            for (Map.Entry<String, String> entry : cidImages.entrySet()) {
                html = html.replace("cid:" + entry.getKey(), entry.getValue());
            }
            email.setBodyHtml(html);
        }
        if (email.getBodyHtml() == null && email.getBodyPlainText() == null) {
            List<String> allImages = new ArrayList<>(cidImages.values());
            allImages.addAll(standaloneImages);
            if (!allImages.isEmpty()) {
                StringBuilder sb = new StringBuilder(
                        "<div style=\"text-align:center;padding:16px;background:#fff;\">");
                for (String dataUri : allImages) {
                    sb.append("<img src=\"").append(dataUri)
                            .append("\" style=\"max-width:100%;display:block;margin:0 auto 16px;\">");
                }
                sb.append("</div>");
                email.setBodyHtml(sb.toString());
            }
        }
    }

    private String extractEmail(String from) {
        if (from.contains("<") && from.contains(">")) {
            return from.substring(from.indexOf("<") + 1, from.indexOf(">"));
        }
        return from;
    }

    private String extractName(String from) {
        if (from.contains("<")) {
            return from.substring(0, from.indexOf("<")).trim();
        }
        return from;
    }
}
