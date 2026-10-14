package com.vishesh.notificationservice.notification;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

@Component 
public class SmsDeliveryMetrics {
    private final Counter smsSent;
    private final Counter smsFailed;

    public SmsDeliveryMetrics(MeterRegistry meterRegistry) {

        this.smsSent = Counter.builder("order.notifications.sms.sent")
                .description("Number of SMS provider requests accepted successfully")
                .register(meterRegistry);

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
