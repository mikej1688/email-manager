package com.emailmanager.service;

import com.emailmanager.entity.Email;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Spam and phishing detection service
 */
@Service
@Slf4j
public class SpamDetectionService {

    // Common spam keywords
    private static final List<String> SPAM_KEYWORDS = Arrays.asList(
            "click here", "act now", "limited time", "buy now", "order now",
            "free money", "risk free", "no obligation", "winner", "congratulations",
            "prize", "lottery", "casino", "viagra", "pharmacy", "weight loss",
            "work from home", "make money fast", "earn extra cash");

    // Phishing indicators
    private static final List<String> PHISHING_KEYWORDS = Arrays.asList(
            "verify your account", "confirm your identity", "urgent action required",
            "suspend your account", "unusual activity", "click here immediately",
            "update your information", "verify payment", "confirm password");

    // Suspicious URL patterns
    private static final Pattern SUSPICIOUS_URL_PATTERN = Pattern.compile(
            "(https?://)?\\d+\\.\\d+\\.\\d+\\.\\d+|" + // IP addresses
                    "bit\\.ly|tinyurl|goo\\.gl|" + // URL shorteners
                    "\\.(xyz|top|work|click)/" // Suspicious TLDs
    );

    /**
     * Detect spam and phishing in an email
     */
    public void detectSpamAndPhishing(Email email) {
        double spamScore = calculateSpamScore(email);
        double phishingScore = calculatePhishingScore(email);

        email.setSpamScore(spamScore);
        email.setPhishingScore(phishingScore);

        // Mark as spam if score is above threshold
        if (spamScore > 0.6) {
            email.setIsSpam(true);
            email.setCategory(Email.EmailCategory.SPAM);
            log.warn("Spam detected: {}", email.getSubject());
        }

        // Mark as phishing if score is above threshold
        if (phishingScore > 0.5) {
            email.setIsPhishing(true);
            email.setCategory(Email.EmailCategory.SPAM);
            log.warn("Phishing detected: {}", email.getSubject());
        }
    }

    /**
     * Calculate spam score (0.0 to 1.0)
     */
    private double calculateSpamScore(Email email) {
        double score = 0.0;
        String subject = email.getSubject().toLowerCase();
        String body = email.getBodyPlainText() != null ? email.getBodyPlainText().toLowerCase() : "";

        // Check for spam keywords
        int keywordMatches = 0;
        for (String keyword : SPAM_KEYWORDS) {
            if (subject.contains(keyword) || body.contains(keyword)) {
                keywordMatches++;
            }
        }
        score += Math.min(keywordMatches * 0.15, 0.6);

        // Check for excessive punctuation
        if (subject.matches(".*[!?]{2,}.*")) {
            score += 0.1;
        }

        // Check for ALL CAPS
        if (subject.equals(subject.toUpperCase()) && subject.length() > 5) {
            score += 0.15;
        }

        // Check for suspicious URLs
        if (SUSPICIOUS_URL_PATTERN.matcher(body).find()) {
            score += 0.2;
        }

        // Check for excessive links
        int linkCount = countOccurrences(body, "http");
        if (linkCount > 5) {
            score += 0.15;
        }

        return Math.min(score, 1.0);
    }

    /**
     * Calculate phishing score (0.0 to 1.0)
     */
    private double calculatePhishingScore(Email email) {
        double score = 0.0;
        String subject = email.getSubject().toLowerCase();
        String body = email.getBodyPlainText() != null ? email.getBodyPlainText().toLowerCase() : "";
        String from = email.getFromAddress().toLowerCase();

        // Check for phishing keywords
        int keywordMatches = 0;
        for (String keyword : PHISHING_KEYWORDS) {
            if (subject.contains(keyword) || body.contains(keyword)) {
                keywordMatches++;
                score += 0.2;
            }
        }

        // Check for mismatched sender domain
        if (isSuspiciousSenderDomain(from, body)) {
            score += 0.3;
        }

        // Check for urgent language
        if (subject.contains("urgent") || subject.contains("immediate") ||
                subject.contains("action required")) {
            score += 0.15;
        }

        // Check for password/credential requests
        if (body.contains("password") || body.contains("credit card") ||
                body.contains("ssn") || body.contains("social security")) {
            score += 0.25;
        }

        // Check for suspicious links claiming to be from known companies
        if (hasSpookedBrandLinks(body)) {
            score += 0.3;
        }

        return Math.min(score, 1.0);
    }

    /**
     * Check if sender domain is suspicious
     */
    private boolean isSuspiciousSenderDomain(String from, String body) {
        // Extract domain from email
        if (!from.contains("@")) {
            return true;
        }

        String domain = from.substring(from.indexOf("@") + 1);

        // Check if body mentions a different company
        List<String> knownBrands = Arrays.asList(
                "paypal", "amazon", "bank", "microsoft", "apple", "google");

        for (String brand : knownBrands) {
            if (body.contains(brand) && !domain.contains(brand)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Check for links claiming to be from known brands
     */
    private boolean hasSpookedBrandLinks(String body) {
        return body.contains("paypal") && !body.contains("paypal.com") ||
                body.contains("amazon") && !body.contains("amazon.com") ||
                body.contains("microsoft") && !body.contains("microsoft.com");
    }

    /**
     * Count occurrences of a substring
     */
    private int countOccurrences(String text, String substring) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(substring, index)) != -1) {
            count++;
            index += substring.length();
        }
        return count;
    }
}
