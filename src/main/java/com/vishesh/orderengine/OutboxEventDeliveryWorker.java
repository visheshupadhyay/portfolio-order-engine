package com.vishesh.orderengine;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

@Service
public class OutboxEventDeliveryWorker {
    private final OutboxEventRepository outboxEventRepository;
    private final OrderPaidNotificationService orderPaidNotificationService;
    // Attempts are counted after a failed provider call: failures 1 and 2 retry; 3 is terminal.
    private static final int MAX_DELIVERY_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MINUTES = 1;

    public OutboxEventDeliveryWorker(OutboxEventRepository outboxEventRepository,
            OrderPaidNotificationService orderPaidNotificationService) {

        this.orderPaidNotificationService = orderPaidNotificationService;
        this.outboxEventRepository = outboxEventRepository;

    }

    public void deliverReadyEvents(LocalDateTime now, int limit) {
        // This method runs after payment has committed, so it sees only durable outbox rows.
        List<OutboxEvent> events = outboxEventRepository.findPendingReadyForDelivery(now, limit);
        for (OutboxEvent event : events) {
            Order order = new Order(event.orderId());
            try {
                // Mark SENT only after the provider call succeeds.
                orderPaidNotificationService.notifyOrderPaid(order);
                outboxEventRepository.markSent(event.id(), now);
            } catch (RuntimeException e) {
                // Catch inside the loop: one bad event must not block later events in this batch.
                if (event.attemptCount() + 1 < MAX_DELIVERY_ATTEMPTS) {
                    outboxEventRepository.rescheduleAfterFailure(event.id(), e.getMessage(),
                            now.plusMinutes(RETRY_DELAY_MINUTES));
                } else {
                    outboxEventRepository.markFailed(event.id(), e.getMessage());
                }
            }

        }
    }
}
