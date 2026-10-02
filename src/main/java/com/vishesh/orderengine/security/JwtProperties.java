package com.vishesh.orderengine.security;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "security.jwt")
/*
 * Typed configuration for token behavior. The Base64 signing secret comes
 * from an environment variable (or ignored local .env), never source control.
 */
public record JwtProperties(String issuer,
        String base64Secret,
        Duration accessTokenTtl) {

}
