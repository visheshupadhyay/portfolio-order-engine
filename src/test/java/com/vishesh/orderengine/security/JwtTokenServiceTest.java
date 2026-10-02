package com.vishesh.orderengine.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;

/*
 * Fixed clocks make token issuance, tampering, and expiry tests deterministic.
 */
class JwtTokenServiceTest {

    private static final String ISSUER = "order-engine";

    // Exactly 32 bytes before Base64 encoding: suitable for HS256.
    private static final String BASE64_SECRET = Base64.getEncoder()
            .encodeToString("01234567890123456789012345678901"
                    .getBytes(StandardCharsets.UTF_8));

    private static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");

    private JwtTokenService tokenService(Clock clock) {
        return new JwtTokenService(
                ISSUER,
                BASE64_SECRET,
                Duration.ofMinutes(5),
                clock);
    }

    // A valid signed token returns the original subject and roles.
    @Test
    void issuesAndParsesWriterToken() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        JwtTokenService service = tokenService(clock);

        String token = service.issue(
                "order-writer",
                Set.of("ORDER_WRITER"));

        JwtPrincipal principal = service.parse(token);

        assertEquals("order-writer", principal.username());
        assertEquals(Set.of("ORDER_WRITER"), principal.roles());
    }

    // One changed signature character makes verification fail.
    @Test
    void rejectsTokenWithChangedContents() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        JwtTokenService service = tokenService(clock);

        String token = service.issue(
                "order-writer",
                Set.of("ORDER_WRITER"));

        String[] parts = token.split("\\.");

        String changedSignature = (parts[2].charAt(0) == 'a' ? "b" : "a")
                + parts[2].substring(1);

        String tamperedToken = parts[0] + "." + parts[1] + "." + changedSignature;

        assertThrows(JwtException.class,
                () -> service.parse(tamperedToken));
    }

    // The parser clock moves past the expiry, so the same token is rejected.
    @Test
    void rejectsExpiredToken() {
        Clock issueClock = Clock.fixed(NOW, ZoneOffset.UTC);
        JwtTokenService issuingService = tokenService(issueClock);

        String token = issuingService.issue(
                "order-writer",
                Set.of("ORDER_WRITER"));

        Clock expiredClock = Clock.fixed(
            NOW.plusSeconds(360L),
                ZoneOffset.UTC);

        JwtTokenService expiredService = tokenService(expiredClock);

        assertThrows(ExpiredJwtException.class,
                () -> expiredService.parse(token));
    }
}
