package com.vishesh.orderengine.outbox;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "order.outbox.observability")
// Operations can decide what "too old" means without recompiling Java.
public record OutboxObservabilityProperties(Duration overdueAfter) {
}
