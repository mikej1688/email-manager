package com.emailmanager.config;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * JPA AttributeConverter that transparently encrypts sensitive email columns
 * using AES-256-GCM before writing to the database and decrypts them on read.
 *
 * A fresh random 12-byte IV is generated for every encryption operation, so
 * two encryptions of the same plaintext produce different ciphertexts.
 * The IV is prepended to the ciphertext and the whole thing is Base64-encoded
 * for storage.
 *
 * Graceful migration: if decryption fails (e.g. an existing unencrypted row),
 * the raw stored value is returned as-is so the app stays readable during a
 * gradual migration.
 */
@Converter
@Component
@Slf4j
public class AttributeEncryptor implements AttributeConverter<String, String> {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12; // 96-bit IV — recommended for GCM
    private static final int GCM_TAG_LENGTH_BITS = 128; // 128-bit authentication tag

    /**
     * Static reference so that any Hibernate-instantiated converter instances
     * (which bypass Spring constructor injection) still see the configured key.
     */
    private static volatile byte[] sharedKey;

    public AttributeEncryptor(@Value("${encryption.key}") String keyBase64) {
        byte[] keyBytes = Base64.getDecoder().decode(keyBase64);
        if (keyBytes.length != 32) {
            throw new IllegalArgumentException(
                    "encryption.key must be a Base64-encoded 256-bit (32-byte) AES key, got " + keyBytes.length
                            + " bytes");
        }
        sharedKey = keyBytes;
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(sharedKey, "AES"),
                    new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));

            byte[] ciphertext = cipher.doFinal(attribute.getBytes(StandardCharsets.UTF_8));

            // Prepend IV: [ iv (12 bytes) | ciphertext + GCM tag ]
            byte[] combined = new byte[GCM_IV_LENGTH + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, GCM_IV_LENGTH);
            System.arraycopy(ciphertext, 0, combined, GCM_IV_LENGTH, ciphertext.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("Failed to encrypt column value", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        try {
            byte[] combined = Base64.getDecoder().decode(dbData);
            if (combined.length <= GCM_IV_LENGTH) {
                // Too short to contain a valid IV + ciphertext — must be legacy plaintext
                return dbData;
            }

            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] ciphertext = new byte[combined.length - GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);
            System.arraycopy(combined, GCM_IV_LENGTH, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE,
                    new SecretKeySpec(sharedKey, "AES"),
                    new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));

            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception e) {
            // Graceful fallback: return raw value if it is legacy unencrypted data
            log.debug("Column decryption failed — treating stored value as legacy plaintext");
            return dbData;
        }
    }
}
