package com.vishesh.orderengine.outbox;

import com.vishesh.orderengine.order.Order;
import com.vishesh.orderengine.notification.OrderPaidNotificationService;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class OutboxEventDeliveryWorker {
    private final OutboxEventRepository outboxEventRepository;
    private final OrderPaidNotificationService orderPaidNotificationService;

    // Typed operational settings come from Spring configuration, allowing each
    // environment to tune retries and claim recovery without changing worker logic.
    private final OutboxDeliveryProperties outboxDeliveryProperties;

    public OutboxEventDeliveryWorker(OutboxEventRepository outboxEventRepository,
            OrderPaidNotificationService orderPaidNotificationService,
            OutboxDeliveryProperties outboxDeliveryProperties) {

        this.orderPaidNotificationService = orderPaidNotificationService;
        this.outboxEventRepository = outboxEventRepository;
        this.outboxDeliveryProperties = outboxDeliveryProperties;
    }

    public void deliverReadyEvents(LocalDateTime now, int limit) {
        // This method runs after payment has committed, so it sees only durable outbox
        // rows.
        // Recovery comes before claiming so an event stuck by a prior crash can be
        // picked up in this same polling run.
        outboxEventRepository.releaseExpiredClaims(now.minus(outboxDeliveryProperties.claimTimeout()));
        List<OutboxEvent> events = outboxEventRepository.claimPendingReadyForDelivery(now, limit);
        for (OutboxEvent event : events) {
            Order order = new Order(event.orderId());
            try {
                // Mark SENT only after the provider call succeeds. The claim token
                // proves this worker still owns the event when it writes the result.
                orderPaidNotificationService.notifyOrderPaid(order, event.id());
                outboxEventRepository.markSent(event.id(), event.claimToken(), now);
            } catch (RuntimeException e) {
                // Catch inside the loop: one bad event must not block later events in this
                // batch.
                if (event.attemptCount() + 1 < outboxDeliveryProperties.maxAttempts()) {
                    outboxEventRepository.rescheduleAfterFailure(event.id(), event.claimToken(), e.getMessage(),
                            now.plus(outboxDeliveryProperties.retryDelay()));
                } else {
                    outboxEventRepository.markFailed(event.id(), event.claimToken(), e.getMessage());
                }
            }

        }
    }
}
