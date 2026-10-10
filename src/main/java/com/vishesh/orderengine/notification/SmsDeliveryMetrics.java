package com.vishesh.orderengine.notification;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

@Component
public class SmsDeliveryMetrics {
    private final Counter smsSent;
    private final Counter smsFailed;

    public SmsDeliveryMetrics(MeterRegistry meterRegistry) {
        // "Sent" means the SMS provider accepted our HTTP request (202). It does
        // not claim that a mobile phone has already received the text.
        this.smsSent = Counter.builder("order.notifications.sms.sent")
                .description("Number of SMS provider requests accepted successfully")
                .register(meterRegistry);

        // Failures include provider errors, timeouts, an open circuit breaker,
        // and a full bulkhead: all are useful signals for Kafka retry/DLT health.
        this.smsFailed = Counter.builder("order.notifications.sms.failed")
                .description("Number of SMS provider request attempts that failed")
                .register(meterRegistry);
    }

    public void recordSent() {
        smsSent.increment();
    }

    public void recordFailed() {
        smsFailed.increment();
    }
}
