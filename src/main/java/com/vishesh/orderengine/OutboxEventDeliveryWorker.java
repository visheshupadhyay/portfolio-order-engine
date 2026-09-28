package com.vishesh.orderengine;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

@Service
public class OutboxEventDeliveryWorker {
    private final OutboxEventRepository outboxEventRepository;
    private final OrderPaidNotificationService orderPaidNotificationService;
    // Attempts are counted after a failed provider call: failures 1 and 2 retry; 3
    // is terminal.
    private static final int MAX_DELIVERY_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MINUTES = 1;
    // A crashed worker cannot clear PROCESSING itself. This lease timeout lets a
    // later polling run safely make abandoned work eligible again.
    private static final long CLAIM_TIMEOUT_MINUTES = 5;

    public OutboxEventDeliveryWorker(OutboxEventRepository outboxEventRepository,
            OrderPaidNotificationService orderPaidNotificationService) {

        this.orderPaidNotificationService = orderPaidNotificationService;
        this.outboxEventRepository = outboxEventRepository;

    }

    public void deliverReadyEvents(LocalDateTime now, int limit) {
        // This method runs after payment has committed, so it sees only durable outbox
        // rows.
        // Recovery comes before claiming so an event stuck by a prior crash can be
        // picked up in this same polling run.
        outboxEventRepository.releaseExpiredClaims(now.minusMinutes(CLAIM_TIMEOUT_MINUTES));
        List<OutboxEvent> events = outboxEventRepository.claimPendingReadyForDelivery(now, limit);
        for (OutboxEvent event : events) {
            Order order = new Order(event.orderId());
            try {
                // Mark SENT only after the provider call succeeds. The claim token
                // proves this worker still owns the event when it writes the result.
                orderPaidNotificationService.notifyOrderPaid(order);
                outboxEventRepository.markSent(event.id(), event.claimToken(), now);
            } catch (RuntimeException e) {
                // Catch inside the loop: one bad event must not block later events in this
                // batch.
                if (event.attemptCount() + 1 < MAX_DELIVERY_ATTEMPTS) {
                    outboxEventRepository.rescheduleAfterFailure(event.id(), event.claimToken(), e.getMessage(),
                            now.plusMinutes(RETRY_DELAY_MINUTES));
                } else {
                    outboxEventRepository.markFailed(event.id(), event.claimToken(), e.getMessage());
                }
            }

        }
    }
}
