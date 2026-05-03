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

import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import com.emailmanager.entity.EmailFolder;
import com.emailmanager.repository.EmailFolderRepository;

/**
 * Gmail API integration service
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class GmailService implements EmailProviderService {

    private static final String APPLICATION_NAME = "Email Manager";
    private static final int GMAIL_CONNECT_TIMEOUT_MS = 10_000;
    private static final int GMAIL_READ_TIMEOUT_MS = 30_000;
    private final OAuth2Service oAuth2Service;
    private final EmailFolderRepository emailFolderRepository;

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

    // System label IDs to sync, in priority order (category tabs before INBOX so
    // the folder assignment in resolveFolder sees the most-specific label first).
    private static final List<String> LABELS_TO_SYNC = List.of(
            "INBOX", "SENT", "DRAFT", "SPAM", "TRASH",
            "CATEGORY_SOCIAL", "CATEGORY_PROMOTIONS", "CATEGORY_UPDATES", "CATEGORY_FORUMS");

    @Override
    public List<Email> fetchNewEmails(EmailAccount account, int limit) {
        List<Email> emails = new ArrayList<>();
        try {
            if (Thread.currentThread().isInterrupted()) {
                log.info("Skipping Gmail fetch for {} because the sync thread was interrupted",
                        account.getEmailAddress());
                return emails;
            }

            Gmail service = getGmailService(account);

            // Fetch all label metadata once to resolve user-created label names
            Map<String, String> labelMap = fetchLabelMap(service);

            // Per-sync folder cache
            Map<String, EmailFolder> folderCache = new java.util.HashMap<>();

            // Time filter for incremental syncs (empty string on first sync = no filter)
            String afterFilter = buildAfterFilter(account);

            // Build time filter for incremental syncs (empty on first sync = fetch all)
            // Use "after:" filter for incremental syncs; no query at all on initial sync
            // so that EVERY message is returned regardless of label.
            // includeSpamTrash=true ensures spam and trash are not silently excluded.
            String query = afterFilter.isEmpty() ? null : afterFilter;
            boolean isInitialSync = afterFilter.isEmpty();
            log.info("Gmail fetch: query='{}', afterFilter='{}', initialSync={}", query, afterFilter, isInitialSync);
            String pageToken = null;
            int pageNum = 0;

            do {
                if (Thread.currentThread().isInterrupted())
                    break;

                com.google.api.services.gmail.Gmail.Users.Messages.List req = service.users().messages().list("me")
                        .setIncludeSpamTrash(true)
                        .setMaxResults(50L);
                if (query != null)
                    req.setQ(query);
                if (pageToken != null)
                    req.setPageToken(pageToken);

                com.google.api.services.gmail.model.ListMessagesResponse response = req.execute();
                List<Message> stubs = response.getMessages();
                pageNum++;
                log.info("Gmail list page {}: got {} stubs, nextPageToken={}, resultSizeEstimate={}",
                        pageNum,
                        stubs == null ? 0 : stubs.size(),
                        response.getNextPageToken(),
                        response.getResultSizeEstimate());

                if (stubs == null || stubs.isEmpty())
                    break;

                for (Message stub : stubs) {
                    if (Thread.currentThread().isInterrupted())
                        break;
                    try {
                        Message full = service.users().messages()
                                .get("me", stub.getId())
                                .setFormat("full")
                                .execute();
                        emails.add(convertGmailToEmail(full, account, service, labelMap, folderCache));
                    } catch (Exception e) {
                        log.warn("Skipping message {} — fetch failed: {}", stub.getId(), e.getMessage());
                    }
                }

                pageToken = response.getNextPageToken();

                if (isInitialSync) {
                    // Lazy-load: only fetch the first page synchronously.
                    // Persist the next page token so the background task can continue.
                    account.setGmailBackgroundPageToken(pageToken);
                    log.info("Initial sync: first page loaded ({} emails). " +
                            "Remaining history will load in background (morePages={}).",
                            emails.size(), pageToken != null);
                    break;
                }
            } while (pageToken != null && !Thread.currentThread().isInterrupted());

            log.info("Fetched {} emails total from Gmail account: {}", emails.size(), account.getEmailAddress());
            return emails; // normal completion — caller can mark initialSyncComplete
        } catch (Exception e) {
            log.error("Failed to fetch emails from Gmail account: {}", account.getEmailAddress(), e);
        }
        return null; // null signals abnormal termination — caller must NOT mark initialSyncComplete
    }

    /**
     * Fetches the next page of historical Gmail emails using the page token stored
     * on the account ({@code gmailBackgroundPageToken}). Called by the background
     * loading task in {@code EmailSyncService} after the initial sync page.
     *
     * <p>
     * On success the token on {@code account} is updated to the next page
     * token (or cleared to {@code null} when the mailbox history is exhausted).
     *
     * @return the fetched emails, or {@code null} if a rate-limit or error
     *         occurred (the token is left unchanged so the task can retry later)
     */
    public List<Email> fetchNextBackgroundPage(EmailAccount account) {
        if (account.getGmailBackgroundPageToken() == null) {
            return List.of(); // nothing left to load
        }
        List<Email> emails = new ArrayList<>();
        try {
            Gmail service = getGmailService(account);
            Map<String, String> labelMap = fetchLabelMap(service);
            Map<String, EmailFolder> folderCache = new java.util.HashMap<>();

            com.google.api.services.gmail.Gmail.Users.Messages.List req = service.users().messages().list("me")
                    .setIncludeSpamTrash(true)
                    .setMaxResults(50L)
                    .setPageToken(account.getGmailBackgroundPageToken());

            com.google.api.services.gmail.model.ListMessagesResponse response = req.execute();
            List<Message> stubs = response.getMessages();

            if (stubs != null) {
                for (Message stub : stubs) {
                    if (Thread.currentThread().isInterrupted())
                        break;
                    try {
                        Message full = service.users().messages()
                                .get("me", stub.getId())
                                .setFormat("full")
                                .execute();
                        emails.add(convertGmailToEmail(full, account, service, labelMap, folderCache));
                    } catch (Exception e) {
                        log.warn("Background sync: skipping message {} — {}", stub.getId(), e.getMessage());
                    }
                }
            }

            // Advance (or clear) the token ONLY on success
            account.setGmailBackgroundPageToken(response.getNextPageToken());
            log.info("Background sync page: {} emails fetched for {}, morePages={}",
                    emails.size(), account.getEmailAddress(), response.getNextPageToken() != null);
            return emails;
        } catch (Exception e) {
            // Detect rate-limit errors (HTTP 429 / Google quota errors)
            String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
            if (msg.contains("429") || msg.contains("ratelimitexceeded") || msg.contains("rate limit")) {
                log.warn("Gmail rate limit hit during background sync for {} — will retry next sync cycle",
                        account.getEmailAddress());
            } else {
                log.error("Background sync page failed for {}: {}", account.getEmailAddress(), e.getMessage());
            }
            return null; // null = stop; token unchanged so it can be retried
        }
    }

    /**
     * Fetch ALL messages for a single Gmail label, paging through every page until
     * exhausted. On incremental syncs the {@code afterFilter} bounds the results
     * naturally, so pages are typically very short. On the initial full sync no
     * filter is applied and all pages are walked.
     *
     * Message IDs already present in {@code seenIds} are skipped so that messages
     * carrying multiple labels (e.g. INBOX + CATEGORY_PROMOTIONS) are only
     * converted and added once.
     */
    private void fetchMessagesForLabel(Gmail service, EmailAccount account,
            String labelId, String afterFilter,
            java.util.Set<String> seenIds, List<Email> results,
            Map<String, String> labelMap, Map<String, EmailFolder> folderCache) {
        try {
            String query = afterFilter.isEmpty() ? "-in:chats" : "-in:chats " + afterFilter;
            String pageToken = null;
            int fetched = 0;

            do {
                com.google.api.services.gmail.Gmail.Users.Messages.List req = service.users().messages().list("me")
                        .setLabelIds(List.of(labelId))
                        .setQ(query)
                        .setMaxResults(50L); // Gmail API max page size
                if (pageToken != null)
                    req.setPageToken(pageToken);

                com.google.api.services.gmail.model.ListMessagesResponse response = req.execute();
                List<Message> stubs = response.getMessages();
                if (stubs == null || stubs.isEmpty())
                    break;

                for (Message stub : stubs) {
                    if (Thread.currentThread().isInterrupted())
                        return;
                    if (!seenIds.add(stub.getId()))
                        continue; // deduplicate across labels

                    Message full = service.users().messages()
                            .get("me", stub.getId())
                            .setFormat("full")
                            .execute();

                    results.add(convertGmailToEmail(full, account, service, labelMap, folderCache));
                    fetched++;
                }

                pageToken = response.getNextPageToken();
            } while (pageToken != null && !Thread.currentThread().isInterrupted());

            if (fetched > 0) {
                log.debug("Fetched {} messages for label {}", fetched, labelId);
            }
        } catch (Exception e) {
            log.warn("Failed to fetch messages for label {} ({}): {}", labelId,
                    labelMap.getOrDefault(labelId, labelId), e.getMessage());
        }
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
            service.users().messages().delete("me", messageId).execute();
            return true;
        } catch (Exception e) {
            log.error("Failed to permanently delete email", e);
            return false;
        }
    }

    public boolean trashEmail(EmailAccount account, String messageId) {
        try {
            Gmail service = getGmailService(account);
            service.users().messages().trash("me", messageId).execute();
            return true;
        } catch (Exception e) {
            log.error("Failed to move email to trash", e);
            return false;
        }
    }

    @Override
    public boolean sendEmail(EmailAccount account, String to, String subject, String body) {
        return sendComposedEmail(account, to, "", subject, body, false);
    }

    /**
     * Send a composed email with CC and HTML support via Gmail API.
     */
    public boolean sendComposedEmail(EmailAccount account, String to, String cc, String subject, String body,
            boolean isHtml) {
        try {
            Gmail service = getGmailService(account);
            MimeMessage mimeMessage = createMimeMessage(account.getEmailAddress(), to, cc, subject, body, isHtml, null);
            Message message = createGmailMessage(mimeMessage);
            service.users().messages().send("me", message).execute();
            log.info("Email sent successfully from {} to {}", account.getEmailAddress(), to);
            return true;
        } catch (RuntimeException e) {
            log.error("Failed to send email from {}: {}", account.getEmailAddress(), e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Failed to send email from {}", account.getEmailAddress(), e);
            return false;
        }
    }

    /**
     * Reply to an email via Gmail API, threading the reply into the conversation.
     */
    public boolean replyToEmail(EmailAccount account, String originalMessageId,
            String replyToAddress, String originalTo, String originalCc,
            String originalSubject, String body, boolean replyAll, boolean isHtml) {
        try {
            Gmail service = getGmailService(account);

            // Get the original message to find its threadId
            Message original = service.users().messages().get("me", originalMessageId).setFormat("metadata").execute();
            String threadId = original.getThreadId();

            // Find the original Message-ID header for In-Reply-To
            String inReplyTo = null;
            if (original.getPayload() != null && original.getPayload().getHeaders() != null) {
                for (var h : original.getPayload().getHeaders()) {
                    if ("Message-ID".equalsIgnoreCase(h.getName()) || "Message-Id".equalsIgnoreCase(h.getName())) {
                        inReplyTo = h.getValue();
                        break;
                    }
                }
            }

            String to = replyToAddress;
            String cc = "";
            if (replyAll) {
                cc = buildReplyAllCc(replyToAddress, originalTo, originalCc, account.getEmailAddress());
            }

            String replySubject = originalSubject != null && originalSubject.startsWith("Re:") ? originalSubject
                    : "Re: " + originalSubject;
            MimeMessage mimeMessage = createMimeMessage(account.getEmailAddress(), to, cc, replySubject, body, isHtml,
                    inReplyTo);
            Message replyMessage = createGmailMessage(mimeMessage);
            replyMessage.setThreadId(threadId);

            service.users().messages().send("me", replyMessage).execute();
            log.info("Reply sent successfully from {} to {}", account.getEmailAddress(), to);
            return true;
        } catch (Exception e) {
            log.error("Failed to send reply from {}", account.getEmailAddress(), e);
            return false;
        }
    }

    /**
     * Archive an email by removing it from INBOX.
     */
    public boolean archiveEmail(EmailAccount account, String messageId) {
        try {
            Gmail service = getGmailService(account);
            com.google.api.services.gmail.model.ModifyMessageRequest request = new com.google.api.services.gmail.model.ModifyMessageRequest()
                    .setRemoveLabelIds(List.of("INBOX"));
            service.users().messages().modify("me", messageId, request).execute();
            log.info("Email archived: {}", messageId);
            return true;
        } catch (Exception e) {
            log.error("Failed to archive email", e);
            return false;
        }
    }

    private MimeMessage createMimeMessage(String from, String to, String cc, String subject, String body,
            boolean isHtml, String inReplyTo) throws Exception {
        Properties props = new Properties();
        Session session = Session.getDefaultInstance(props, null);
        MimeMessage mimeMessage = new MimeMessage(session);
        mimeMessage.setFrom(new InternetAddress(from));
        mimeMessage.setRecipients(jakarta.mail.Message.RecipientType.TO,
                RecipientListUtils.toInternetAddresses(to));
        if (cc != null && !cc.isEmpty()) {
            mimeMessage.setRecipients(jakarta.mail.Message.RecipientType.CC,
                    RecipientListUtils.toInternetAddresses(cc));
        }
        mimeMessage.setSubject(subject);
        if (isHtml) {
            mimeMessage.setContent(body, "text/html; charset=utf-8");
        } else {
            mimeMessage.setText(body, "utf-8");
        }
        if (inReplyTo != null) {
            mimeMessage.setHeader("In-Reply-To", inReplyTo);
            mimeMessage.setHeader("References", inReplyTo);
        }
        return mimeMessage;
    }

    private Message createGmailMessage(MimeMessage mimeMessage) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        mimeMessage.writeTo(buffer);
        byte[] bytes = buffer.toByteArray();
        String encodedEmail = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Message message = new Message();
        message.setRaw(encodedEmail);
        return message;
    }

    @Override
    public boolean isConnected(EmailAccount account) {
        return authenticate(account);
    }

    private Gmail getGmailService(EmailAccount account) {
        // getCredential() already logs auth/token errors; let RuntimeException
        // propagate directly so we don't emit duplicate ERROR log entries.
        Credential credential = oAuth2Service.getCredential(account);

        try {
            return new Gmail.Builder(
                    new NetHttpTransport(),
                    GsonFactory.getDefaultInstance(),
                    request -> {
                        credential.initialize(request);
                        request.setConnectTimeout(GMAIL_CONNECT_TIMEOUT_MS);
                        request.setReadTimeout(GMAIL_READ_TIMEOUT_MS);
                    })
                    .setApplicationName(APPLICATION_NAME)
                    .build();
        } catch (Exception e) {
            log.error("Failed to build Gmail service for account: {}", account.getEmailAddress(), e);
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

    private Email convertGmailToEmail(Message gmailMessage, EmailAccount account, Gmail gmailService,
            Map<String, String> labelMap, Map<String, EmailFolder> folderCache) {
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

        // Map Gmail labels to category, folder, read/starred status
        List<String> labelIds = gmailMessage.getLabelIds() != null ? gmailMessage.getLabelIds() : List.of();
        email.setIsRead(!labelIds.contains("UNREAD"));
        email.setIsStarred(labelIds.contains("STARRED"));
        email.setCategory(mapLabelsToCategory(labelIds));
        email.setFolder(resolveFolder(account, labelIds, labelMap, folderCache));
        // Store all label IDs comma-padded so label-membership queries can use LIKE '%,ID,%'
        if (!labelIds.isEmpty()) {
            email.setGmailLabelIds("," + String.join(",", labelIds) + ",");
        }

        // Guard NOT NULL columns against messages with missing headers (automated/system mail)
        if (email.getSubject() == null) email.setSubject("(no subject)");
        if (email.getFromAddress() == null) email.setFromAddress("");

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
                Charset charset = extractCharset(part);
                email.setBodyHtml(new String(Base64.getUrlDecoder().decode(data), charset));
            }
        } else if (mimeType.equals("text/plain")) {
            String data = part.getBody() != null ? part.getBody().getData() : null;
            if (data != null && email.getBodyPlainText() == null) {
                Charset charset = extractCharset(part);
                email.setBodyPlainText(new String(Base64.getUrlDecoder().decode(data), charset));
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
                email.setBodyPlainText(new String(Base64.getUrlDecoder().decode(data), StandardCharsets.UTF_8));
            }
        }
        // Other types (application/pdf, etc.) are intentionally skipped
    }

    /**
     * Extracts the charset from a MIME part's Content-Type header (e.g. "text/html;
     * charset=iso-8859-1").
     * Falls back to UTF-8 if the header is absent or the charset is unrecognised.
     */
    private Charset extractCharset(com.google.api.services.gmail.model.MessagePart part) {
        if (part.getHeaders() != null) {
            for (com.google.api.services.gmail.model.MessagePartHeader h : part.getHeaders()) {
                if ("content-type".equalsIgnoreCase(h.getName()) && h.getValue() != null) {
                    for (String param : h.getValue().split(";")) {
                        String trimmed = param.trim();
                        if (trimmed.toLowerCase().startsWith("charset=")) {
                            String name = trimmed.substring("charset=".length()).replaceAll("[\"']", "").trim();
                            try {
                                return Charset.forName(name);
                            } catch (Exception ignored) {
                                // Unrecognised charset — fall through to UTF-8
                            }
                        }
                    }
                }
            }
        }
        return StandardCharsets.UTF_8;
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

    /**
     * Returns a Gmail "after:" time filter string, or empty string on first sync.
     */
    private String buildAfterFilter(EmailAccount account) {
        if (!Boolean.TRUE.equals(account.getInitialSyncComplete()) || account.getLastSyncTime() == null)
            return "";
        long epochSec = account.getLastSyncTime().toEpochSecond(java.time.ZoneOffset.UTC);
        return "after:" + epochSec;
    }

    /**
     * Eagerly creates an {@link EmailFolder} row for every user-created Gmail label
     * that doesn't already have one. System labels (INBOX, SENT, TRASH, etc.) are
     * intentionally skipped — they are handled via {@code Email.category}.
     *
     * <p>Also back-fills {@code gmailLabelId} on existing folders that were created
     * before this field existed (matched by display name).
     */
    public void syncGmailLabelsAsFolders(EmailAccount account) {
        try {
            Gmail service = getGmailService(account);
            var labels = service.users().labels().list("me").execute().getLabels();
            if (labels == null) return;

            for (var label : labels) {
                String id = label.getId();
                // Skip system labels — routed via EmailCategory, not EmailFolder
                if (SYSTEM_LABEL_NAMES.containsKey(id) || NON_FOLDER_LABELS.contains(id))
                    continue;

                String name = label.getName() != null ? label.getName() : id;

                // Find by label ID first, fall back to name (for rows created before gmailLabelId existed)
                EmailFolder folder = emailFolderRepository
                        .findByAccountAndGmailLabelId(account, id)
                        .orElseGet(() -> emailFolderRepository.findByAccountAndName(account, name)
                                .orElse(null));

                if (folder == null) {
                    folder = new EmailFolder();
                    folder.setAccount(account);
                    folder.setName(name);
                    folder.setIsSystemFolder(false);
                }
                // Back-fill gmailLabelId if missing
                if (folder.getGmailLabelId() == null) {
                    folder.setGmailLabelId(id);
                    emailFolderRepository.save(folder);
                }
            }
            log.info("Gmail label-to-folder sync complete for {}", account.getEmailAddress());
        } catch (Exception e) {
            log.warn("Failed to sync Gmail labels as folders for {}: {}", account.getEmailAddress(), e.getMessage());
        }
    }

    /** Fetch all label metadata and return a map of labelId → display name. */
    private Map<String, String> fetchLabelMap(Gmail service) {
        try {
            var labels = service.users().labels().list("me").execute().getLabels();
            if (labels == null)
                return Map.of();
            Map<String, String> map = new java.util.HashMap<>();
            for (var label : labels) {
                map.put(label.getId(), label.getName());
            }
            return map;
        } catch (Exception e) {
            log.warn("Could not fetch Gmail labels; user-label folders will use label ID as name", e);
            return Map.of();
        }
    }

    /**
     * Map Gmail label IDs to the internal EmailCategory (highest-priority label
     * wins).
     */
    private Email.EmailCategory mapLabelsToCategory(List<String> labelIds) {
        if (labelIds.contains("TRASH"))
            return Email.EmailCategory.TRASH;
        if (labelIds.contains("SPAM"))
            return Email.EmailCategory.SPAM;
        if (labelIds.contains("DRAFT"))
            return Email.EmailCategory.DRAFT;
        if (labelIds.contains("SENT"))
            return Email.EmailCategory.SENT;
        if (labelIds.contains("CATEGORY_SOCIAL"))
            return Email.EmailCategory.SOCIAL;
        if (labelIds.contains("CATEGORY_PROMOTIONS"))
            return Email.EmailCategory.PROMOTIONS;
        if (labelIds.contains("CATEGORY_UPDATES"))
            return Email.EmailCategory.UPDATES;
        if (labelIds.contains("CATEGORY_FORUMS"))
            return Email.EmailCategory.FORUMS;
        if (labelIds.contains("IMPORTANT"))
            return Email.EmailCategory.IMPORTANT;
        return Email.EmailCategory.INBOX;
    }

    private static final Map<String, String> SYSTEM_LABEL_NAMES = Map.ofEntries(
            Map.entry("INBOX", "Inbox"),
            Map.entry("SENT", "Sent"),
            Map.entry("TRASH", "Trash"),
            Map.entry("SPAM", "Spam"),
            Map.entry("DRAFT", "Drafts"),
            Map.entry("IMPORTANT", "Important"),
            Map.entry("STARRED", "Starred"),
            Map.entry("CATEGORY_SOCIAL", "Social"),
            Map.entry("CATEGORY_PROMOTIONS", "Promotions"),
            Map.entry("CATEGORY_UPDATES", "Updates"),
            Map.entry("CATEGORY_FORUMS", "Forums"));

    // Labels that are metadata only and never map to a folder on their own.
    private static final java.util.Set<String> NON_FOLDER_LABELS = java.util.Set.of(
            "UNREAD", "STARRED", "IMPORTANT", "CHAT", "CATEGORY_PERSONAL");

    /**
     * Returns an EmailFolder only for user-created Gmail labels.
     * Returns null for every built-in system label (INBOX, SENT, TRASH, SPAM,
     * DRAFT, CATEGORY_PROMOTIONS, etc.).
     *
     * The app uses two separate routing mechanisms:
     * - email.category (enum, folder=null) → queried by /category/{cat}
     * - email.folder (FK, folder!=null) → queried by /folder/{id}
     * Setting folder to a non-null value for system labels would break the
     * category-based queries because the repository uses
     * findByAccountAndCategoryAndFolderIsNull.
     */
    private EmailFolder resolveFolder(EmailAccount account, List<String> labelIds,
            Map<String, String> labelMap, Map<String, EmailFolder> folderCache) {

        for (String id : labelIds) {
            // A label that isn't a known system label and isn't metadata-only is a
            // user-created label — give it its own EmailFolder row.
            if (!SYSTEM_LABEL_NAMES.containsKey(id) && !NON_FOLDER_LABELS.contains(id)) {
                String name = labelMap.getOrDefault(id, id);
                return findOrCreateFolder(account, name, id, false, folderCache);
            }
        }

        // All labels are system labels — routing is done via email.category, no folder
        // needed.
        return null;
    }

    private EmailFolder findOrCreateFolder(EmailAccount account, String name, String gmailLabelId, boolean isSystem,
            Map<String, EmailFolder> folderCache) {
        // The cache is keyed by folder name. computeIfAbsent ensures the DB is only
        // queried (and the row only created) once per folder per sync run, even when
        // hundreds of messages share the same label.
        return folderCache.computeIfAbsent(name, n -> emailFolderRepository.findByAccountAndName(account, n)
                .orElseGet(() -> {
                    EmailFolder f = new EmailFolder();
                    f.setAccount(account);
                    f.setName(n);
                    f.setGmailLabelId(gmailLabelId);
                    f.setIsSystemFolder(isSystem);
                    return emailFolderRepository.save(f);
                }));
    }

    private String buildReplyAllCc(String to, String originalTo, String originalCc, String accountEmailAddress) {
        LinkedHashMap<String, String> recipients = new LinkedHashMap<>();
        LinkedHashSet<String> toRecipients = new LinkedHashSet<>();

        collectRecipientKeys(toRecipients, to);
        appendUniqueRecipients(recipients, originalTo, toRecipients, accountEmailAddress);
        appendUniqueRecipients(recipients, originalCc, toRecipients, accountEmailAddress);

        return String.join(", ", recipients.values());
    }

    private void appendUniqueRecipients(LinkedHashMap<String, String> recipients, String recipientList,
            LinkedHashSet<String> excludedRecipients, String accountEmailAddress) {
        if (recipientList == null || recipientList.isBlank()) {
            return;
        }

        String ownAddress = normalizeRecipient(accountEmailAddress);
        for (String part : RecipientListUtils.splitRecipientList(recipientList)) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            String normalized = normalizeRecipient(trimmed);
            if (normalized.isEmpty() || normalized.equals(ownAddress) || excludedRecipients.contains(normalized)) {
                continue;
            }
            recipients.putIfAbsent(normalized, trimmed);
        }
    }

    private void collectRecipientKeys(LinkedHashSet<String> recipients, String recipientList) {
        if (recipientList == null || recipientList.isBlank()) {
            return;
        }

        for (String part : RecipientListUtils.splitRecipientList(recipientList)) {
            String normalized = normalizeRecipient(part);
            if (!normalized.isEmpty()) {
                recipients.add(normalized);
            }
        }
    }

    private String normalizeRecipient(String recipient) {
        return RecipientListUtils.normalizeRecipient(recipient);
    }
}
