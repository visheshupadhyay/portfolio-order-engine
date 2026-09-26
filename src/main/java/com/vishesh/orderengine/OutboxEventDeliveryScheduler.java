package com.vishesh.orderengine;

import java.time.LocalDateTime;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "order.outbox.delivery.enabled", havingValue = "true")
public class OutboxEventDeliveryScheduler {
    private final OutboxEventDeliveryWorker outboxEventDeliveryWorker;

    public OutboxEventDeliveryScheduler (OutboxEventDeliveryWorker outboxEventDeliveryWorker) {
        this.outboxEventDeliveryWorker = outboxEventDeliveryWorker;
    }

    // fixedDelay waits until the previous poll finishes before waiting five seconds again.
    // The bean is absent unless deployment explicitly enables the scheduler property.
    @Scheduled(fixedDelay = 5000)
    public void deliverDueEvents() {
        outboxEventDeliveryWorker.deliverReadyEvents(LocalDateTime.now(), 100);
    }
}
