package com.vishesh.orderengine.outbox;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Profile("!postgres & !jpa")
@Repository
public class InMemoryOutboxEventRepository implements OutboxEventRepository {

    private List<OutboxEvent> events = new ArrayList<>();
    private Long nextId = 1L;

    @Override
    public void enqueueOrderPaid(String orderId) {
        // Mirror database defaults so default-profile tests exercise the same
        // lifecycle.
        LocalDateTime currentTime = LocalDateTime.now();
        OutboxEvent event = new OutboxEvent(nextId++,
                null,
                orderId,
                "ORDER_PAID",
                OutboxEventStatus.PENDING,
                0,
                currentTime,
                null,
                currentTime,
                null,
                null);

        events.add(event);
    }

    public List<OutboxEvent> findAll() {
        // Package-private test helper; production code consumes only the interface
        // methods.
        return List.copyOf(events);
    }

    @Override
    public synchronized List<OutboxEvent> claimPendingReadyForDelivery(LocalDateTime now, int limit) {
        // synchronized makes selection and replacement one indivisible operation
        // inside this one JVM. PostgreSQL achieves the equivalent across servers.
        // "not after now" means nextAttemptAt <= now. Sorting matches the JDBC query.
        List<OutboxEvent> filteredEvents = events.stream()
                .filter(event -> event.status() == OutboxEventStatus.PENDING && !event.nextAttemptAt().isAfter(now))
                .sorted(Comparator.comparing(OutboxEvent::nextAttemptAt).thenComparing(OutboxEvent::id))
                .limit(limit)
                .toList();

        List<OutboxEvent> claimedEvents = new ArrayList<>();

        for (OutboxEvent event : filteredEvents) {
            // Every claim receives a new token. If this lease later expires, a
            // reclaimed event will have a different token and reject stale writes.
            OutboxEvent processingEvent = new OutboxEvent(
                    event.id(),
                    UUID.randomUUID().toString(),
                    event.orderId(),
                    event.eventType(),
                    OutboxEventStatus.PROCESSING,
                    event.attemptCount(),
                    event.nextAttemptAt(),
                    now,
                    event.createdAt(),
                    event.sentAt(),
                    event.lastError());

            int storedIndex = events.indexOf(event);
            events.set(storedIndex, processingEvent);
            claimedEvents.add(processingEvent);
        }
        return claimedEvents;
    }

    @Override
    public synchronized void markSent(long eventId, String claimToken, LocalDateTime sentAt) {
        for (int i = 0; i < events.size(); i++) {
            OutboxEvent event = events.get(i);
            if (event.status() == OutboxEventStatus.PROCESSING
                    && event.id() == eventId
                    && claimToken != null
                    && claimToken.equals(event.claimToken())) {
                // Records are immutable. The token check means a worker that lost
                // its lease cannot mark a newer worker's event as SENT.
                OutboxEvent newEvent = new OutboxEvent(event.id(), null, event.orderId(),
                        event.eventType(),
                        OutboxEventStatus.SENT, event.attemptCount(), event.nextAttemptAt(), null,
                        event.createdAt(), sentAt,
                        null);
                events.set(i, newEvent);
                break;
            }
        }
    }

    @Override
    public synchronized void rescheduleAfterFailure(long eventId, String claimToken, String error,
            LocalDateTime nextAttemptAt) {
        for (int i = 0; i < events.size(); i++) {
            OutboxEvent event = events.get(i);
            if (event.status() == OutboxEventStatus.PROCESSING
                    && event.id() == eventId
                    && claimToken != null
                    && claimToken.equals(event.claimToken())) {
                // Keep it PENDING, but move its eligibility into the future for a retry.
                OutboxEvent newEvent = new OutboxEvent(event.id(), null, event.orderId(),
                        event.eventType(),
                        OutboxEventStatus.PENDING, event.attemptCount() + 1, nextAttemptAt, null,
                        event.createdAt(), null,
                        error);
                events.set(i, newEvent);
                break;
            }
        }
    }

    @Override
    public synchronized void markFailed(long eventId, String claimToken, String error) {
        for (int i = 0; i < events.size(); i++) {
            OutboxEvent event = events.get(i);
            if (event.status() == OutboxEventStatus.PROCESSING
                    && event.id() == eventId
                    && claimToken != null
                    && claimToken.equals(event.claimToken())) {
                // FAILED is terminal: claimPendingReadyForDelivery will never return it again.
                OutboxEvent newEvent = new OutboxEvent(event.id(), null, event.orderId(),
                        event.eventType(),
                        OutboxEventStatus.FAILED, event.attemptCount() + 1, event.nextAttemptAt(), null,
                        event.createdAt(),
                        null,
                        error);
                events.set(i, newEvent);
                break;
            }
        }
    }

    @Override
    public synchronized int releaseExpiredClaims(LocalDateTime claimedBefore) {
        int releasedEvents = 0;
        for (int i = 0; i < events.size(); i++) {
            OutboxEvent event = events.get(i);
            if (event.status() == OutboxEventStatus.PROCESSING
                    && event.claimedAt() != null
                    && event.claimedAt().compareTo(claimedBefore) <= 0) {

                // Expiry is not evidence that the provider failed: clear ownership
                // and retry without incrementing attemptCount.
                OutboxEvent newEvent = new OutboxEvent(event.id(), null, event.orderId(),
                        event.eventType(),
                        OutboxEventStatus.PENDING, event.attemptCount(), event.nextAttemptAt(), null,
                        event.createdAt(), event.sentAt(),
                        event.lastError());
                events.set(i, newEvent);
                releasedEvents++;
            }
        }
        return releasedEvents;
    }

    @Override
    public synchronized long countPendingDueBefore(LocalDateTime cutoff) {
        // Keep the local/test implementation semantically identical to the SQL
        // query so the dashboard means the same thing in every profile.
        return events.stream()
                .filter(event -> event.status() == OutboxEventStatus.PENDING && !event.nextAttemptAt().isAfter(cutoff))
                .count();
    }
}
