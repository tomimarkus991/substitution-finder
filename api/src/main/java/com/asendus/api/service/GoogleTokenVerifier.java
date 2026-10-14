package com.asendus.api.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/** Checks a Google ID token from the client's Google sign-in. Only used once per login. */
@Slf4j
@Component
public class GoogleTokenVerifier {

    private static final String JWK_SET_URI = "https://www.googleapis.com/oauth2/v3/certs";
    private static final Set<String> ISSUERS = Set.of("https://accounts.google.com", "accounts.google.com");

    private final JwtDecoder decoder;

    public GoogleTokenVerifier(@Value("${app.auth.google-client-ids:}") List<String> clientIds) {
        List<String> accepted = clientIds.stream().map(String::trim).filter(id -> !id.isEmpty()).toList();
        if (accepted.isEmpty()) {
            log.warn("No 'app.auth.google-client-ids' (GOOGLE_CLIENT_IDS) configured — every Google sign-in will be rejected.");
        }

        // Keys are fetched lazily on the first login, so startup doesn't need the network
        NimbusJwtDecoder nimbus = NimbusJwtDecoder.withJwkSetUri(JWK_SET_URI).build();
        nimbus.setJwtValidator(validator(accepted));
        this.decoder = nimbus;
    }

    /** Expiry, issuer, audience (one of our client IDs) and a verified email. */
    static OAuth2TokenValidator<Jwt> validator(Collection<String> clientIds) {
        return new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(),
                new JwtClaimValidator<Object>(JwtClaimNames.ISS, iss -> iss != null && ISSUERS.contains(iss.toString())),
                new JwtClaimValidator<List<String>>(JwtClaimNames.AUD, aud -> aud != null && aud.stream().anyMatch(clientIds::contains)),
                new JwtClaimValidator<Object>("email_verified", verified -> Boolean.TRUE.equals(verified) || "true".equals(verified))
        );
    }

    public GoogleIdentity verify(String idToken) {
        Jwt jwt;
        try {
            jwt = decoder.decode(idToken);
        } catch (BadJwtException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Google token");
        } catch (JwtException e) {
            // Not the token's fault, e.g. Google's keys couldn't be fetched
            log.warn("Could not verify Google token: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not verify Google token");
        }

        return new GoogleIdentity(jwt.getSubject(), jwt.getClaimAsString("email"), jwt.getClaimAsString("name"));
    }

    public record GoogleIdentity(String sub, String email, String name) {}
}
