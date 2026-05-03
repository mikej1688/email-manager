package com.emailmanager.service;

import com.emailmanager.entity.Email;
import com.emailmanager.entity.EmailAccount;
import com.emailmanager.repository.EmailAccountRepository;
import com.emailmanager.repository.EmailRepository;
import com.emailmanager.service.email.EmailProviderService;
import com.emailmanager.service.email.GmailService;
import com.emailmanager.service.email.ImapEmailService;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Value;

/**
 * Service for synchronizing emails from various email accounts
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EmailSyncService {

    private final EmailAccountRepository emailAccountRepository;
    private final EmailRepository emailRepository;
    private final GmailService gmailService;
    private final ImapEmailService imapEmailService;
    private final EmailClassificationService classificationService;

    @Autowired
    @Qualifier("taskScheduler")
    private TaskScheduler taskScheduler;

    @Value("${email.sync.fetch.limit:50}")
    private int fetchLimit;

    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);

    @PreDestroy
    public void onShutdown() {
        log.info("Application shutting down — stopping email synchronization");
        shuttingDown.set(true);
    }

    /**
     * Sync all active email accounts
     * Runs every 5 minutes
     */
    @Scheduled(fixedRate = 300000) // 5 minutes
    @Transactional
    public void syncAllAccounts() {
        if (shuttingDown.get() || Thread.currentThread().isInterrupted()) {
            log.info("Skipping scheduled sync — application is shutting down");
            return;
        }
        log.info("Starting email synchronization for all accounts");
        List<EmailAccount> activeAccounts = emailAccountRepository.findByIsActiveTrue();

        for (EmailAccount account : activeAccounts) {
            if (shuttingDown.get() || Thread.currentThread().isInterrupted()) {
                log.info("Aborting sync loop — application is shutting down");
                break;
            }
            try {
                if (account.getAccessToken() == null && account.getRefreshToken() == null) {
                    log.warn("Skipping sync for unauthorized account: {} — complete OAuth authorization first",
                            account.getEmailAddress());
                    continue;
                }
                syncAccount(account);
            } catch (Exception e) {
                log.error("Failed to sync account: {}", account.getEmailAddress(), e);
            }
        }

        log.info("Completed email synchronization for {} accounts", activeAccounts.size());
    }

    /**
     * Sync a specific email account
     */
    @Transactional
    public void syncAccount(EmailAccount account) {
        if (shuttingDown.get() || Thread.currentThread().isInterrupted()) {
            log.info("Skipping account sync for {} during shutdown", account.getEmailAddress());
            return;
        }

        log.info("Syncing account: {}", account.getEmailAddress());

        EmailProviderService providerService = getProviderService(account);

        // Proactively create EmailFolder rows for every user-created Gmail label so
        // folders appear in the sidebar even before an email with that label is synced.
        if (account.getProvider() == EmailAccount.EmailProvider.GMAIL) {
            gmailService.syncGmailLabelsAsFolders(account);
        }

        // Fetch new emails — returns null if the fetch failed fatally (e.g. network
        // error on the list call itself), so we must NOT mark the sync complete.
        List<Email> newEmails = providerService.fetchNewEmails(account, fetchLimit);
        if (newEmails == null) {
            log.warn("fetchNewEmails returned null for {} — skipping save and not marking sync complete",
                    account.getEmailAddress());
            return;
        }

        // Save new emails
        int savedCount = 0;
        for (Email email : newEmails) {
            if (shuttingDown.get() || Thread.currentThread().isInterrupted()) {
                log.info("Stopping email save loop for {} during shutdown", account.getEmailAddress());
                break;
            }
            // Check if email already exists
            if (emailRepository.findByMessageId(email.getMessageId()).isEmpty()) {
                // Persist first so the email has a DB ID before classification
                // creates any Notification rows that reference it.
                email = emailRepository.save(email);
                classificationService.classifyEmail(email);
                emailRepository.save(email); // persist classification updates
                savedCount++;

                log.debug("Saved new email: {} from {}", email.getSubject(), email.getFromAddress());
            }
        }

        if (account.getProvider() == EmailAccount.EmailProvider.GMAIL
                && !Boolean.TRUE.equals(account.getInitialSyncComplete())) {
            account.setInitialSyncComplete(true);
        }

        // Update last sync time
        account.setLastSyncTime(LocalDateTime.now());
        emailAccountRepository.save(account);

        log.info("Synced {} new emails for account: {}", savedCount, account.getEmailAddress());

        // If Gmail still has historical pages pending, schedule the background loader.
        // This fires after the first-page initial sync AND after any sync cycle where
        // the background task was previously interrupted by a rate limit.
        if (account.getProvider() == EmailAccount.EmailProvider.GMAIL
                && account.getGmailBackgroundPageToken() != null
                && !shuttingDown.get()) {
            final Long accountId = account.getId();
            taskScheduler.schedule(() -> backgroundLoadRemainingPages(accountId), Instant.now());
            log.info("Background Gmail history load scheduled for {}", account.getEmailAddress());
        }
    }

    /**
     * Loads the remaining pages of Gmail history in the background, one page at a
     * time, using the page token stored on the account. Stops automatically when:
     * <ul>
     * <li>all pages are exhausted (token becomes null), or</li>
     * <li>Gmail returns a rate-limit error (token is kept for the next retry),
     * or</li>
     * <li>the application is shutting down.</li>
     * </ul>
     * Each page's emails are saved individually so the UI can show them as they
     * arrive (the frontend paginates from the local DB).
     */
    public void backgroundLoadRemainingPages(Long accountId) {
        if (shuttingDown.get())
            return;

        EmailAccount account = emailAccountRepository.findById(accountId).orElse(null);
        if (account == null || account.getGmailBackgroundPageToken() == null)
            return;

        log.info("Background Gmail history load started for {}", account.getEmailAddress());

        while (account.getGmailBackgroundPageToken() != null
                && !shuttingDown.get()
                && !Thread.currentThread().isInterrupted()) {

            List<Email> page = gmailService.fetchNextBackgroundPage(account);

            if (page == null) {
                // Rate-limited or transient error — persist the current token and stop.
                // The next scheduled sync will re-submit this task to retry.
                emailAccountRepository.save(account);
                log.warn("Background Gmail history load paused for {} — will retry on next sync cycle",
                        account.getEmailAddress());
                return;
            }

            int saved = 0;
            for (Email email : page) {
                if (shuttingDown.get() || Thread.currentThread().isInterrupted())
                    break;
                if (emailRepository.findByMessageId(email.getMessageId()).isEmpty()) {
                    email = emailRepository.save(email);
                    classificationService.classifyEmail(email);
                    emailRepository.save(email);
                    saved++;
                }
            }

            // Persist the updated page token (may now be null = exhausted)
            emailAccountRepository.save(account);
            log.info("Background Gmail history: saved {}/{} emails for {}, morePages={}",
                    saved, page.size(), account.getEmailAddress(),
                    account.getGmailBackgroundPageToken() != null);
        }

        if (account.getGmailBackgroundPageToken() == null) {
            log.info("Background Gmail history load complete for {} — full mailbox loaded",
                    account.getEmailAddress());
        }
    }

    /**
     * Get the appropriate provider service based on account type
     */
    private EmailProviderService getProviderService(EmailAccount account) {
        return switch (account.getProvider()) {
            case GMAIL -> gmailService;
            case YAHOO, OUTLOOK, IMAP_GENERIC -> imapEmailService;
        };
    }

    /**
     * Force a full re-sync for an account by clearing the incremental-sync state
     * flags first. This causes {@code buildAfterFilter} in GmailService to return
     * an empty filter so every email in every label is fetched from Gmail, not
     * just emails newer than the last sync time.
     */
    @Transactional
    public void fullResyncAccount(EmailAccount account) {
        log.info("Starting full re-sync for account: {} (resetting sync state)", account.getEmailAddress());
        // Reset all incremental-sync and background-load flags so fetchNewEmails
        // starts from the beginning and background loading restarts from page 1.
        account.setInitialSyncComplete(false);
        account.setLastSyncTime(null);
        account.setGmailBackgroundPageToken(null);
        emailAccountRepository.save(account);

        // Now run a normal sync — it will fetch ALL emails because initialSyncComplete
        // is false, and will set initialSyncComplete=true again on completion.
        syncAccount(account);
    }

    /**
     * Test connection to an email account
     */
    public boolean testConnection(EmailAccount account) {
        EmailProviderService providerService = getProviderService(account);
        return providerService.isConnected(account);
    }
}
