package com.vishesh.orderengine.security;

import java.util.Date;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.crypto.SecretKey;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

/*
 * Issues and verifies signed access tokens. JWT claims are readable by a
 * holder, not encrypted; the HMAC signature proves the server issued them.
 */
public class JwtTokenService {
    private final String issuer;
    private final String base64Secret;
    private final Duration accessTokenTtl;
    private final Clock clock;
    private final SecretKey signingKey;

    public JwtTokenService(String issuer,
            String base64Secret,
            Duration accessTokenTtl,
            Clock clock) {

        this.accessTokenTtl = accessTokenTtl;
        this.base64Secret = base64Secret;
        this.issuer = issuer;
        this.clock = clock;
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(this.base64Secret));
    }
    public String issue(String username, Set<String> roles) {
        Instant now = clock.instant();
        return Jwts.builder()
                .issuer(issuer)
                .subject(username)
                // Claims provide identity and authorization only; never add passwords.
                .claim("roles", roles)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTokenTtl)))
                .signWith(signingKey)
                .compact();
    }
    public JwtPrincipal parse(String token) {
        // Verify signature, issuer, and expiry before trusting any JWT claim.
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(issuer)
                .clock(() -> Date.from(clock.instant()))
                .build()
                .parseSignedClaims(token)
                .getPayload();

        List<?> roleValues = claims.get("roles", List.class);

        if (roleValues == null) {
            throw new JwtException("JWT is missing roles");
        }

        Set<String> roles = roleValues.stream()
                .map(Object::toString)
                .collect(Collectors.toUnmodifiableSet());

        return new JwtPrincipal(claims.getSubject(), roles);
    }
}
