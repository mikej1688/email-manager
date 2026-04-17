package com.emailmanager.service;

import com.emailmanager.entity.Email;
import com.emailmanager.entity.Notification;
import com.emailmanager.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Service for sending notifications to users
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    /**
     * Send urgent email alert
     */
    public void sendUrgentEmailAlert(Email email) {
        Notification notification = new Notification();
        notification.setEmail(email);
        notification.setType(Notification.NotificationType.URGENT_EMAIL);
        notification.setMessage(String.format(
                "Urgent email from %s: %s",
                email.getFromAddress(),
                email.getSubject()));
        notification.setSentAt(LocalDateTime.now());

        notificationRepository.save(notification);
        email.setUserNotified(true);

        log.info("Sent urgent email notification for: {}", email.getSubject());
    }

    /**
     * Send deadline notification
     */
    public void sendDeadlineNotification(Email email) {
        Notification notification = new Notification();
        notification.setEmail(email);
        notification.setType(Notification.NotificationType.DEADLINE_APPROACHING);
        notification.setMessage(String.format(
                "Email with deadline %s: %s",
                email.getDueDate(),
                email.getSubject()));
        notification.setSentAt(LocalDateTime.now());

        notificationRepository.save(notification);
        email.setUserNotified(true);

        log.info("Sent deadline notification for: {}", email.getSubject());
    }

    /**
     * Send phishing alert
     */
    public void sendPhishingAlert(Email email) {
        Notification notification = new Notification();
        notification.setEmail(email);
        notification.setType(Notification.NotificationType.PHISHING_DETECTED);
        notification.setMessage(String.format(
                "Phishing email detected from %s: %s",
                email.getFromAddress(),
                email.getSubject()));
        notification.setSentAt(LocalDateTime.now());

        notificationRepository.save(notification);
        email.setUserNotified(true);

        log.warn("Sent phishing alert for: {}", email.getSubject());
    }

    /**
     * Send account sync failed notification
     */
    public void sendAccountSyncFailed(String accountEmail, String errorMessage) {
        Notification notification = new Notification();
        notification.setType(Notification.NotificationType.ACCOUNT_SYNC_FAILED);
        notification.setMessage(String.format(
                "Failed to sync account %s: %s",
                accountEmail,
                errorMessage));
        notification.setSentAt(LocalDateTime.now());

        notificationRepository.save(notification);

        log.error("Sent account sync failed notification for: {}", accountEmail);
    }
}
