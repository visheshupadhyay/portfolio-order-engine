package com.vishesh.orderengine.outbox;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "order.outbox.delivery")
public record OutboxDeliveryProperties(boolean enabled,
        Duration pollDelay,
        int batchSize,
        Duration retryDelay,
        Duration claimTimeout,
        int maxAttempts) {

}
