package com.asendus.api.service;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;

class GoogleTokenVerifierTest {

    private final OAuth2TokenValidator<Jwt> validator = GoogleTokenVerifier.validator(List.of("our-client-id"));

    @Test
    void acceptsAValidToken() {
        assertThat(validator.validate(token(b -> b)).hasErrors()).isFalse();
    }

    @Test
    void acceptsTheIssuerWithoutScheme() {
        assertThat(validator.validate(token(b -> b.issuer("accounts.google.com"))).hasErrors()).isFalse();
    }

    @Test
    void rejectsAnotherAppsAudience() {
        assertThat(validator.validate(token(b -> b.audience(List.of("someone-elses-client-id")))).hasErrors()).isTrue();
    }

    @Test
    void rejectsAnUnverifiedEmail() {
        assertThat(validator.validate(token(b -> b.claim("email_verified", false))).hasErrors()).isTrue();
    }

    @Test
    void rejectsAnotherIssuer() {
        assertThat(validator.validate(token(b -> b.issuer("https://evil.example.com"))).hasErrors()).isTrue();
    }

    @Test
    void rejectsAnExpiredToken() {
        Instant past = Instant.now().minus(Duration.ofHours(3));
        assertThat(validator.validate(token(b -> b.issuedAt(past).expiresAt(past.plus(Duration.ofHours(1))))).hasErrors()).isTrue();
    }

    private Jwt token(UnaryOperator<Jwt.Builder> customize) {
        Instant now = Instant.now();
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("google-sub")
                .issuer("https://accounts.google.com")
                .audience(List.of("our-client-id"))
                .claim("email", "tomi@example.com")
                .claim("email_verified", true)
                .issuedAt(now)
                .expiresAt(now.plus(Duration.ofHours(1)));
        return customize.apply(builder).build();
    }
}
