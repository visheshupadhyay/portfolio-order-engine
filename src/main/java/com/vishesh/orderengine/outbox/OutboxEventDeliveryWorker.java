package com.vishesh.orderengine.outbox;

import com.vishesh.orderengine.message.OrderPaidEventPublisher;
import com.vishesh.orderengine.message.OrderPaidMessage;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
/*
 * Bridge from the PostgreSQL transactional outbox to Kafka.
 *
 * Payment writes an outbox row in the same database transaction. A later worker
 * claims that durable row, publishes its stable event ID to Kafka, then marks
 * the row SENT only after Kafka acknowledges it. This avoids losing an event
 * when the application crashes between payment and publication.
 */
public class OutboxEventDeliveryWorker {
    private static final Logger logger = LoggerFactory.getLogger(OutboxEventDeliveryWorker.class);
    private final OutboxEventRepository outboxEventRepository;
    private final OrderPaidEventPublisher orderPaidEventPublisher;
    private final OutboxDeliveryMetrics outboxDeliveryMetrics;

    // Typed operational settings come from Spring configuration, allowing each
    // environment to tune retries and claim recovery without changing worker logic.
    private final OutboxDeliveryProperties outboxDeliveryProperties;

    public OutboxEventDeliveryWorker(OutboxEventRepository outboxEventRepository,
            OutboxDeliveryProperties outboxDeliveryProperties,
            OutboxDeliveryMetrics outboxDeliveryMetrics,
            OrderPaidEventPublisher orderPaidEventPublisher) {

        this.outboxEventRepository = outboxEventRepository;
        this.outboxDeliveryMetrics = outboxDeliveryMetrics;
        this.outboxDeliveryProperties = outboxDeliveryProperties;
        this.orderPaidEventPublisher = orderPaidEventPublisher;
    }

    public void deliverReadyEvents(LocalDateTime now, int limit) {
        long startedAtNanos = System.nanoTime();
        try {
            // This method runs after payment has committed, so it sees only durable outbox
            // rows.
            // Recovery comes before claiming so an event stuck by a prior crash can be
            // picked up in this same polling run.
            int releasedClaims = outboxEventRepository
                    .releaseExpiredClaims(now.minus(outboxDeliveryProperties.claimTimeout()));

            if (releasedClaims > 0) {
                logger.warn("Released {} expired outbox claim(s) for retry", releasedClaims);
                outboxDeliveryMetrics.recordReleasedClaims(releasedClaims);
            }
            List<OutboxEvent> events = outboxEventRepository.claimPendingReadyForDelivery(now, limit);
            logger.info("Outbox delivery run claimed {} event(s)", events.size());
            for (OutboxEvent event : events) {

                try {
                    logger.debug(
                            "Delivering outbox event: eventId={}, orderId={}, attempt={}",
                            event.id(),
                            event.orderId(),
                            event.attemptCount() + 1);

                    OrderPaidMessage message = new OrderPaidMessage(event.id(), event.orderId());
                    orderPaidEventPublisher.publish(message);

                    // Kafka accepted the event, so this outbox row no longer
                    // needs delivery. A crash before this line can cause a
                    // duplicate Kafka event later; consumers handle that using
                    // the permanent event ID.
                    outboxEventRepository.markSent(event.id(), event.claimToken(), now);

                    logger.info(
                            "Outbox event delivered successfully: eventId={}, orderId={}",
                            event.id(),
                            event.orderId());
                    outboxDeliveryMetrics.recordDelivered();
                } catch (RuntimeException e) {
                    // Catch inside the loop: one bad event must not block later events in this
                    // batch.
                    if (event.attemptCount() + 1 < outboxDeliveryProperties.maxAttempts()) {
                        LocalDateTime nextAttemptAt = now.plus(outboxDeliveryProperties.retryDelay());
                        logger.warn(
                                "Kafka publish failed; retry scheduled: eventId={}, orderId={}, attempt={}, maxAttempts={}, nextAttemptAt={}, exceptionType={}",
                                event.id(),
                                event.orderId(),
                                event.attemptCount() + 1,
                                outboxDeliveryProperties.maxAttempts(),
                                nextAttemptAt,
                                e.getClass().getSimpleName());
                        outboxEventRepository.rescheduleAfterFailure(event.id(), event.claimToken(), e.getMessage(),
                                nextAttemptAt);
                        outboxDeliveryMetrics.recordRetried();
                    } else {
                        outboxEventRepository.markFailed(event.id(), event.claimToken(), e.getMessage());
                        logger.error(
                                "Kafka publish failed permanently: eventId={}, orderId={}, attempt={}, maxAttempts={}, exceptionType={}",
                                event.id(),
                                event.orderId(),
                                event.attemptCount() + 1,
                                outboxDeliveryProperties.maxAttempts(),
                                e.getClass().getSimpleName());
                        outboxDeliveryMetrics.recordFailed();
                    }
                }
            }
        } finally {
            Duration runDuration = Duration.ofNanos(System.nanoTime() - startedAtNanos);
            outboxDeliveryMetrics.recordDeliveryRun(runDuration);
        }
    }
}
