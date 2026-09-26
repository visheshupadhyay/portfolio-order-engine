package com.vishesh.orderengine;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Profile("!postgres & !jpa")
@Repository
public class InMemoryOutboxEventRepository implements OutboxEventRepository {

    private List<OutboxEvent> events = new ArrayList<>();
    private Long nextId = 1L;

    @Override
    public void enqueueOrderPaid(String orderId) {
        // Mirror database defaults so default-profile tests exercise the same lifecycle.
        LocalDateTime currentTime = LocalDateTime.now();
        OutboxEvent event = new OutboxEvent(nextId++,
                orderId,
                "ORDER_PAID",
                OutboxEventStatus.PENDING,
                0,
                currentTime,
                currentTime,
                null,
                null);

        events.add(event);
    }

    List<OutboxEvent> findAll() {
        // Package-private test helper; production code consumes only the interface methods.
        return List.copyOf(events);
    }

    @Override
    public List<OutboxEvent> findPendingReadyForDelivery(LocalDateTime now, int limit) {
        // "not after now" means nextAttemptAt <= now. Sorting matches the JDBC query.
        return events.stream()
                .filter(event -> event.status() == OutboxEventStatus.PENDING && !event.nextAttemptAt().isAfter(now))
                .sorted(Comparator.comparing(OutboxEvent::nextAttemptAt).thenComparing(OutboxEvent::id))
                .limit(limit)
                .toList();
    }

    @Override
    public void markSent(long eventId, LocalDateTime sentAt) {
        for (int i = 0; i < events.size(); i++) {
            OutboxEvent event = events.get(i);
            if (event.id() == eventId && event.status() == OutboxEventStatus.PENDING) {
                // Records are immutable, so replace the stored snapshot after a successful delivery.
                OutboxEvent newEvent = new OutboxEvent(event.id(), event.orderId(), event.eventType(),
                        OutboxEventStatus.SENT, event.attemptCount(), event.nextAttemptAt(), event.createdAt(), sentAt,
                        null);
                events.set(i, newEvent);
                break;
            }
        }
    }

    @Override
    public void rescheduleAfterFailure(long eventId, String error, LocalDateTime nextAttemptAt) {
        for (int i = 0; i < events.size(); i++) {
            OutboxEvent event = events.get(i);
            if (event.id() == eventId && event.status() == OutboxEventStatus.PENDING) {
                // Keep it PENDING, but move its eligibility into the future for a retry.
                OutboxEvent newEvent = new OutboxEvent(event.id(), event.orderId(), event.eventType(),
                        OutboxEventStatus.PENDING, event.attemptCount()+1, nextAttemptAt, event.createdAt(), null,
                        error);
                events.set(i, newEvent);
                break;
            }
        }
    }

    @Override
    public void markFailed(long eventId, String error) {
        for (int i = 0; i < events.size(); i++) {
            OutboxEvent event = events.get(i);
            if (event.id() == eventId && event.status() == OutboxEventStatus.PENDING) {
                // FAILED is terminal: findPendingReadyForDelivery will never return it again.
                OutboxEvent newEvent = new OutboxEvent(event.id(), event.orderId(), event.eventType(),
                        OutboxEventStatus.FAILED, event.attemptCount()+1, event.nextAttemptAt(), event.createdAt(), null,
                        error);
                events.set(i, newEvent);
                break;
            }
        }
    }

}
