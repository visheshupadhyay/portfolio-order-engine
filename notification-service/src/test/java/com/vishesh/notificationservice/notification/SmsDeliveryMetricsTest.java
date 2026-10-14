package com.vishesh.notificationservice.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

public class SmsDeliveryMetricsTest {
    @Test
    public void test() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        SmsDeliveryMetrics smsDeliveryMetrics = new SmsDeliveryMetrics(meterRegistry);
        smsDeliveryMetrics.recordSent();;
        smsDeliveryMetrics.recordFailed();
        smsDeliveryMetrics.recordFailed();

        assertEquals(1,meterRegistry.counter("order.notifications.sms.sent").count());
        assertEquals(2,meterRegistry.counter("order.notifications.sms.failed").count());
    }
}
