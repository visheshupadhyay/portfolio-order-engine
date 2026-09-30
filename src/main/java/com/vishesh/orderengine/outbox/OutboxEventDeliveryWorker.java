package com.vishesh.orderengine.outbox;

import com.vishesh.orderengine.order.Order;
import com.vishesh.orderengine.notification.OrderPaidNotificationService;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class OutboxEventDeliveryWorker {
    private static final Logger logger = LoggerFactory.getLogger(OutboxEventDeliveryWorker.class);
    private final OutboxEventRepository outboxEventRepository;
    private final OrderPaidNotificationService orderPaidNotificationService;
    private final OutboxDeliveryMetrics outboxDeliveryMetrics;

    // Typed operational settings come from Spring configuration, allowing each
    // environment to tune retries and claim recovery without changing worker logic.
    private final OutboxDeliveryProperties outboxDeliveryProperties;

    public OutboxEventDeliveryWorker(OutboxEventRepository outboxEventRepository,
            OrderPaidNotificationService orderPaidNotificationService,
            OutboxDeliveryProperties outboxDeliveryProperties,
            OutboxDeliveryMetrics outboxDeliveryMetrics) {

        this.orderPaidNotificationService = orderPaidNotificationService;
        this.outboxEventRepository = outboxEventRepository;
        this.outboxDeliveryMetrics = outboxDeliveryMetrics;
        this.outboxDeliveryProperties = outboxDeliveryProperties;
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
                Order order = new Order(event.orderId());
                try {
                    logger.debug(
                            "Delivering outbox event: eventId={}, orderId={}, attempt={}",
                            event.id(),
                            event.orderId(),
                            event.attemptCount() + 1);
                    // Mark SENT only after the provider call succeeds. The claim token
                    // proves this worker still owns the event when it writes the result.
                    orderPaidNotificationService.notifyOrderPaid(order, event.id());
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
                                "Outbox delivery failed; retry scheduled: eventId={}, orderId={}, attempt={}, maxAttempts={}, nextAttemptAt={}, exceptionType={}",
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
                                "Outbox delivery failed permanently: eventId={}, orderId={}, attempt={}, maxAttempts={}, exceptionType={}",
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
