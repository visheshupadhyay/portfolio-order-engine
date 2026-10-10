package com.vishesh.orderengine.order;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

@Component
public class OrderPaymentMetrics {
    private final Counter completedPayments;

    public OrderPaymentMetrics(MeterRegistry meterRegistry) {
        // Keep metric names in one component instead of scattering string names
        // through business code. Micrometer exposes this Counter to Prometheus as
        // order_payments_completed_total.
        this.completedPayments = Counter.builder("order.payments.completed")
                .description("Number of orders successfully marked as paid")
                .register(meterRegistry);
    }

    public void recordCompleted() {
        // Counters only move forward while this application process lives.
        // Grafana uses rate(...) when it needs a per-minute payment speed.
        completedPayments.increment();
    }
}
