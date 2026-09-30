package com.vishesh.orderengine.outbox;

import java.time.LocalDateTime;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
@ConditionalOnProperty(name = "order.outbox.delivery.enabled", havingValue = "true")
public class OutboxEventDeliveryScheduler {
    private static final Logger logger = LoggerFactory.getLogger(OutboxEventDeliveryScheduler.class);
    private final OutboxEventDeliveryWorker outboxEventDeliveryWorker;
    private final OutboxDeliveryProperties properties;

    public OutboxEventDeliveryScheduler(OutboxEventDeliveryWorker outboxEventDeliveryWorker,
            OutboxDeliveryProperties properties) {
        this.outboxEventDeliveryWorker = outboxEventDeliveryWorker;
        this.properties = properties;
    }

    // fixedDelay waits until the previous poll finishes before waiting the
    // configured poll delay again.
    // The bean is absent unless deployment explicitly enables the scheduler
    // property.
    @Scheduled(fixedDelayString = "${order.outbox.delivery.poll-delay}")
    public void deliverDueEvents() {
        logger.debug("Starting scheduled outbox delivery poll");
        outboxEventDeliveryWorker.deliverReadyEvents(LocalDateTime.now(), properties.batchSize());
    }
}
