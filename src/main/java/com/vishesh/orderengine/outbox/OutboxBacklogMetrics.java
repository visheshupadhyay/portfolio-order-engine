package com.vishesh.orderengine.outbox;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

@Component
public class OutboxBacklogMetrics {

    public OutboxBacklogMetrics(
            MeterRegistry meterRegistry,
            OutboxEventRepository outboxEventRepository,
            OutboxObservabilityProperties outboxObservabilityProperties,
            Clock clock) {

        Gauge.builder(
                "order.outbox.events.overdue",
                outboxEventRepository,
                // A Gauge asks this question every time Prometheus scrapes it.
                // It is a current backlog count, not a counter that only grows.
                repository -> repository.countPendingDueBefore(
                        LocalDateTime.now(clock).minus(outboxObservabilityProperties.overdueAfter())))
                .description("Number of pending outbox events overdue for delivery")
                .register(meterRegistry);
    }
}
