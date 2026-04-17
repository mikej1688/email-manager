package com.emailmanager.service;

import com.emailmanager.entity.Email;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for extracting due dates and deadlines from emails
 */
@Service
@Slf4j
public class DueDateExtractionService {

    // Patterns for date extraction
    private static final Pattern DATE_PATTERN = Pattern.compile(
            "(?:due|deadline|respond by|reply by|complete by|finish by|submit by)\\s+" +
                    "([A-Za-z]+\\s+\\d{1,2},?\\s+\\d{4}|\\d{1,2}/\\d{1,2}/\\d{2,4}|" +
                    "\\d{4}-\\d{2}-\\d{2}|tomorrow|today|next week)",
            Pattern.CASE_INSENSITIVE);

    private static final DateTimeFormatter[] DATE_FORMATS = {
            DateTimeFormatter.ofPattern("MMMM d, yyyy"),
            DateTimeFormatter.ofPattern("MMM d, yyyy"),
            DateTimeFormatter.ofPattern("M/d/yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd")
    };

    /**
     * Extract due date from email content
     */
    public void extractDueDate(Email email) {
        String subject = email.getSubject().toLowerCase();
        String body = email.getBodyPlainText() != null ? email.getBodyPlainText().toLowerCase() : "";

        // Try to find date in subject first
        LocalDateTime dueDate = extractDateFromText(subject);

        // If not found, try body
        if (dueDate == null) {
            dueDate = extractDateFromText(body);
        }

        if (dueDate != null) {
            email.setDueDate(dueDate);
            log.debug("Extracted due date {} from email: {}", dueDate, email.getSubject());
        }
    }

    /**
     * Extract date from text
     */
    private LocalDateTime extractDateFromText(String text) {
        Matcher matcher = DATE_PATTERN.matcher(text);

        if (matcher.find()) {
            String dateStr = matcher.group(1).trim();
            return parseDate(dateStr);
        }

        return null;
    }

    /**
     * Parse date string to LocalDateTime
     */
    private LocalDateTime parseDate(String dateStr) {
        dateStr = dateStr.toLowerCase();

        // Handle relative dates
        if (dateStr.equals("today")) {
            return LocalDate.now().atTime(23, 59);
        } else if (dateStr.equals("tomorrow")) {
            return LocalDate.now().plusDays(1).atTime(23, 59);
        } else if (dateStr.equals("next week")) {
            return LocalDate.now().plusWeeks(1).atTime(23, 59);
        }

        // Try different date formats
        for (DateTimeFormatter formatter : DATE_FORMATS) {
            try {
                LocalDate date = LocalDate.parse(dateStr, formatter);
                return date.atTime(23, 59);
            } catch (Exception e) {
                // Continue to next format
            }
        }

        log.warn("Failed to parse date: {}", dateStr);
        return null;
    }
}
