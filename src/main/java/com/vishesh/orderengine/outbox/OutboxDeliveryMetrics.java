package com.vishesh.orderengine.outbox;

import java.time.Duration;
import io.micrometer.core.instrument.Timer;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

@Component
public class OutboxDeliveryMetrics {
    // These meter objects are registered once at application startup. They keep
    // collecting values that Actuator can later expose to Prometheus.
    private final Counter deliveredEvents;
    private final Counter retriedEvents;
    private final Counter failedEvents;
    private final Counter releasedClaims;
    private final Timer deliveryRunDuration;

    public OutboxDeliveryMetrics(MeterRegistry meterRegistry) {
        this.deliveredEvents = Counter.builder("order.outbox.events.delivered")
                .description("Number of outbox events delivered successfully")
                .register(meterRegistry);

        this.retriedEvents = Counter.builder("order.outbox.events.retried")
                .description("Number of outbox events scheduled for retry")
                .register(meterRegistry);

        this.failedEvents = Counter.builder("order.outbox.events.failed")
                .description("Number of outbox events that failed permanently")
                .register(meterRegistry);

        this.releasedClaims = Counter.builder("order.outbox.claims.released")
                .description("Number of expired outbox claims released for retry")
                .register(meterRegistry);

        this.deliveryRunDuration = Timer.builder("order.outbox.delivery.run")
                .description("Time spent processing one complete outbox delivery run")
                .register(meterRegistry);
    }

    public void recordDelivered() {
        deliveredEvents.increment();
    }

    public void recordRetried() {
        retriedEvents.increment();
    }

    public void recordFailed() {
        failedEvents.increment();
    }

    public void recordReleasedClaims(int count) {
        if (count > 0) {
            releasedClaims.increment(count);
        }
    }

    public void recordDeliveryRun(Duration duration) {
        // Every worker run is timed, regardless of whether its events succeed,
        // retry, or fail. Outcome counters above explain the result separately.
        deliveryRunDuration.record(duration);
    }
}
