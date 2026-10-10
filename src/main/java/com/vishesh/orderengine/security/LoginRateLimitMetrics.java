package com.vishesh.orderengine.security;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

@Component
public class LoginRateLimitMetrics {
    private final Counter loginRejected;

    public LoginRateLimitMetrics(MeterRegistry meterRegistry) {
        // This measures rejected login attempts only; successful logins are not
        // security incidents and therefore do not increment this counter.
        this.loginRejected = Counter.builder("order.security.login.rate_limited")
                .description("Number of login requests rejected by the rate limiter")
                .register(meterRegistry);
    }

    public void recordRejected() {
        loginRejected.increment();
    }
}
