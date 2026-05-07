package com.emailmanager.config;

/**
 * Jackson view markers for role-based field visibility on Email responses.
 *
 * Summary — safe for DEVELOPER role: metadata only, no encrypted PII fields.
 * Full    — USER / ADMIN: all fields including decrypted content.
 *
 * Fields with no @JsonView are always included regardless of active view
 * (requires spring.jackson.mapper.default-view-inclusion=true in application.properties).
 */
public class EmailViews {
    public interface Summary {}
    public interface Full extends Summary {}
}
