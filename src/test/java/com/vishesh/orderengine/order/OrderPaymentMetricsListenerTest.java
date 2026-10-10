package com.vishesh.orderengine.order;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

public class OrderPaymentMetricsListenerTest {

    @Test
    public void recordsCompletedPaymentWhenOrderPaidEventIsHandled() {
        // A small in-memory registry lets the test read the exact Counter value
        // without loading Spring or contacting Prometheus.
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        OrderPaymentMetrics metrics = new OrderPaymentMetrics(registry);
        OrderPaymentMetricsListener metricListener = new OrderPaymentMetricsListener(metrics);
        assertEquals(0.0, registry.counter("order.payments.completed").count());
        // This simulates the listener being called after a successful commit.
        metricListener.onOrderPaid(new OrderPaid("order-101"));
        assertEquals(1.0, registry.counter("order.payments.completed").count());
    }
}
