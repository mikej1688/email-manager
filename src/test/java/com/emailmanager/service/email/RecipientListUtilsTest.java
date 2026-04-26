package com.emailmanager.service.email;

import jakarta.mail.internet.InternetAddress;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RecipientListUtilsTest {

    @Test
    void splitRecipientListSupportsCommaAndSemicolonDelimiters() {
        List<String> recipients = RecipientListUtils.splitRecipientList(
                "first@example.com; second@example.com, third@example.com");

        assertEquals(List.of(
                "first@example.com",
                "second@example.com",
                "third@example.com"), recipients);
    }

    @Test
    void splitRecipientListKeepsDisplayNamesIntact() {
        List<String> recipients = RecipientListUtils.splitRecipientList(
                "\"Doe, Jane\" <jane@example.com>; John Smith <john@example.com>");

        assertEquals(List.of(
                "\"Doe, Jane\" <jane@example.com>",
                "John Smith <john@example.com>"), recipients);
    }

    @Test
    void toInternetAddressesAcceptsSemicolonSeparatedRecipients() throws Exception {
        InternetAddress[] recipients = RecipientListUtils.toInternetAddresses(
                "alpha@example.com; beta@example.com");

        assertEquals(List.of("alpha@example.com", "beta@example.com"),
                List.of(recipients).stream().map(InternetAddress::getAddress).toList());
    }
}