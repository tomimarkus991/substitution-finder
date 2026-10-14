package com.asendus.api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** The emails allowed to use the app (app.auth.allowed-emails, comma-separated), compared case-insensitively. */
@Component
public class EmailAllowlist {
    private final Set<String> emails;

    public EmailAllowlist(@Value("${app.auth.allowed-emails:}") String allowedEmails) {
        this.emails = Arrays.stream(allowedEmails.split(","))
                .map(String::trim).filter(e -> !e.isEmpty())
                .map(e -> e.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    public boolean allows(String email) {
        return email != null && emails.contains(email.trim().toLowerCase(Locale.ROOT));
    }
}
