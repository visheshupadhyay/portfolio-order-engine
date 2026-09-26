package com.vishesh.orderengine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

/* Fast contract tests for the outbox state machine without a database. */
public class InMemoryOutboxEventRepositoryTest {

    @Test
    public void enqueuesPendingOrderPaidEvent() {
        InMemoryOutboxEventRepository inMemoryOutboxEventRepository = new InMemoryOutboxEventRepository();
        inMemoryOutboxEventRepository.enqueueOrderPaid("order-101");
        List<OutboxEvent> events = inMemoryOutboxEventRepository.findAll();
        assertEquals(1, events.size());
        assertEquals(1L, events.get(0).id());
        assertEquals("order-101", events.get(0).orderId());
        assertEquals("ORDER_PAID", events.get(0).eventType());
        assertEquals(OutboxEventStatus.PENDING, events.get(0).status());
        assertEquals(0, events.get(0).attemptCount());
        assertEquals(events.get(0).nextAttemptAt(), events.get(0).createdAt());
        assertNull(events.get(0).sentAt());
        assertNull(events.get(0).lastError());
    }

    @Test
    public void returnsOldestPendingEventsReadyForDelivery() {
        InMemoryOutboxEventRepository inMemoryOutboxEventRepository = new InMemoryOutboxEventRepository();
        inMemoryOutboxEventRepository.enqueueOrderPaid("order-a");
        inMemoryOutboxEventRepository.enqueueOrderPaid("order-b");
        inMemoryOutboxEventRepository.enqueueOrderPaid("order-c");
        // The worker receives only a bounded, oldest-first batch that is due now.
        List<OutboxEvent> events = inMemoryOutboxEventRepository.findPendingReadyForDelivery(LocalDateTime.now(), 2);
        assertEquals(2, events.size());
        assertEquals("order-a", events.get(0).orderId());
        assertEquals("order-b", events.get(1).orderId());
        List<OutboxEvent> zeroEvents = inMemoryOutboxEventRepository
                .findPendingReadyForDelivery(LocalDateTime.now().minusMinutes(1), 2);
        assertEquals(0, zeroEvents.size());
    }

    @Test
    public void marksPendingEventSentAndExcludesItFromReadyDelivery() {
        InMemoryOutboxEventRepository inMemoryOutboxEventRepository = new InMemoryOutboxEventRepository();
        inMemoryOutboxEventRepository.enqueueOrderPaid("order-a");
        inMemoryOutboxEventRepository.enqueueOrderPaid("order-b");
        Long eventId = inMemoryOutboxEventRepository.findAll().stream().findFirst().orElseThrow().id();
        LocalDateTime sentAt = LocalDateTime.now();
        inMemoryOutboxEventRepository.markSent(eventId, sentAt);
        assertEquals(sentAt, inMemoryOutboxEventRepository.findAll().stream().findFirst().orElseThrow().sentAt());
        assertEquals(OutboxEventStatus.SENT,
                inMemoryOutboxEventRepository.findAll().stream().findFirst().orElseThrow().status());
        assertEquals(1, inMemoryOutboxEventRepository.findPendingReadyForDelivery(sentAt.plusMinutes(5), 10).size());
        assertEquals("order-b",
                inMemoryOutboxEventRepository.findPendingReadyForDelivery(sentAt.plusMinutes(5), 10).get(0).orderId());
    }

    @Test
    public void reschedulesFailedPendingEventForLaterRetry() {
        InMemoryOutboxEventRepository inMemoryOutboxEventRepository = new InMemoryOutboxEventRepository();
        inMemoryOutboxEventRepository.enqueueOrderPaid("retry-order-a");
        Long eventId = inMemoryOutboxEventRepository.findAll().get(0).id();
        LocalDateTime retryTime = LocalDateTime.now().plusMinutes(5);
        inMemoryOutboxEventRepository.rescheduleAfterFailure(eventId, "SMS provider timeout", retryTime);
        OutboxEvent event = inMemoryOutboxEventRepository.findAll().get(0);
        assertEquals(OutboxEventStatus.PENDING, event.status());
        assertEquals(1, event.attemptCount());
        assertEquals(retryTime, event.nextAttemptAt());
        assertNull(event.sentAt());
        assertEquals("SMS provider timeout", event.lastError());

        // PENDING does not mean immediately deliverable: the future retry time delays it.
        assertTrue(inMemoryOutboxEventRepository.findPendingReadyForDelivery(LocalDateTime.now(), 10).isEmpty());
        assertEquals(1,
                inMemoryOutboxEventRepository.findPendingReadyForDelivery(LocalDateTime.now().plusMinutes(7), 10)
                        .size());

    }

    @Test
    public void marksPendingEventFailedAndExcludesItFromReadyDelivery() {
        InMemoryOutboxEventRepository inMemoryOutboxEventRepository = new InMemoryOutboxEventRepository();
        inMemoryOutboxEventRepository.enqueueOrderPaid("retry-order-a");
        Long eventId = inMemoryOutboxEventRepository.findAll().get(0).id();

        inMemoryOutboxEventRepository.markFailed(eventId, "SMS provider unavailable");

        OutboxEvent event = inMemoryOutboxEventRepository.findAll().get(0);
        assertEquals(OutboxEventStatus.FAILED,event.status());
        assertEquals(1,event.attemptCount());
        assertEquals("SMS provider unavailable",event.lastError());
        assertNull(event.sentAt());
        // FAILED is a terminal audit state, so the worker never receives it again.
        assertEquals(0, inMemoryOutboxEventRepository.findPendingReadyForDelivery(LocalDateTime.now(), 10).size());
    }
}
