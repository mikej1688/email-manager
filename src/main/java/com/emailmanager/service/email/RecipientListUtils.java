package com.emailmanager.service.email;

import jakarta.mail.internet.InternetAddress;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class RecipientListUtils {

    private RecipientListUtils() {
    }

    public static InternetAddress[] toInternetAddresses(String recipientList) throws Exception {
        List<InternetAddress> addresses = new ArrayList<>();
        for (String recipient : splitRecipientList(recipientList)) {
            InternetAddress[] parsed = InternetAddress.parse(recipient, false);
            for (InternetAddress address : parsed) {
                addresses.add(address);
            }
        }
        return addresses.toArray(InternetAddress[]::new);
    }

    public static List<String> splitRecipientList(String recipientList) {
        if (recipientList == null || recipientList.isBlank()) {
            return List.of();
        }

        List<String> recipients = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        boolean escaped = false;
        int angleDepth = 0;

        for (int i = 0; i < recipientList.length(); i++) {
            char currentChar = recipientList.charAt(i);

            if (escaped) {
                current.append(currentChar);
                escaped = false;
                continue;
            }

            if (currentChar == '\\' && inQuotes) {
                current.append(currentChar);
                escaped = true;
                continue;
            }

            if (currentChar == '"') {
                inQuotes = !inQuotes;
                current.append(currentChar);
                continue;
            }

            if (!inQuotes) {
                if (currentChar == '<') {
                    angleDepth++;
                } else if (currentChar == '>' && angleDepth > 0) {
                    angleDepth--;
                } else if ((currentChar == ',' || currentChar == ';') && angleDepth == 0) {
                    addRecipient(recipients, current);
                    continue;
                }
            }

            current.append(currentChar);
        }

        addRecipient(recipients, current);
        return recipients;
    }

    public static String normalizeRecipient(String recipient) {
        if (recipient == null) {
            return "";
        }

        String trimmed = recipient.trim();
        int start = trimmed.indexOf('<');
        int end = trimmed.indexOf('>');
        if (start >= 0 && end > start) {
            trimmed = trimmed.substring(start + 1, end).trim();
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }

    private static void addRecipient(List<String> recipients, StringBuilder current) {
        String recipient = current.toString().trim();
        if (!recipient.isEmpty()) {
            recipients.add(recipient);
        }
        current.setLength(0);
    }
}