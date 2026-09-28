package com.vishesh.orderengine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

/* Fast contract tests for the outbox state machine without a database. */
/* Mirrors the outbox lifecycle without PostgreSQL. These tests document the same
 * claim, lease-expiry, and stale-token rules the JDBC implementation must honor. */
public class InMemoryOutboxEventRepositoryTest {

	@Test
	public void enqueuesPendingOrderPaidEvent() {
		InMemoryOutboxEventRepository repository = new InMemoryOutboxEventRepository();
		repository.enqueueOrderPaid("order-101");
		List<OutboxEvent> events = repository.findAll();

		assertEquals(1, events.size());
		assertNull(events.get(0).claimedAt());
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
		InMemoryOutboxEventRepository repository = new InMemoryOutboxEventRepository();
		repository.enqueueOrderPaid("order-a");
		repository.enqueueOrderPaid("order-b");
		repository.enqueueOrderPaid("order-c");
		// The worker receives only a bounded, oldest-first batch that is due now.
		LocalDateTime claimAt = LocalDateTime.now().plusSeconds(1);
		List<OutboxEvent> events = repository.claimPendingReadyForDelivery(claimAt, 2);
		assertEquals(2, events.size());
		assertNotNull(events.get(0).claimedAt());
		assertNotNull(events.get(1).claimedAt());
		assertEquals("order-a", events.get(0).orderId());
		assertEquals("order-b", events.get(1).orderId());
		List<OutboxEvent> zeroEvents = repository.claimPendingReadyForDelivery(claimAt.minusMinutes(1), 2);
		assertEquals(0, zeroEvents.size());
		assertTrue(events.get(0).status() == events.get(1).status()
				&& events.get(0).status() == OutboxEventStatus.PROCESSING);
	}

	@Test
	public void marksPendingEventSentAndExcludesItFromReadyDelivery() {
		InMemoryOutboxEventRepository repository = new InMemoryOutboxEventRepository();
		repository.enqueueOrderPaid("order-a");
		repository.enqueueOrderPaid("order-b");

		LocalDateTime claimAt = LocalDateTime.now().plusSeconds(1);
		List<OutboxEvent> processingEvents = repository.claimPendingReadyForDelivery(claimAt, 1);
		assertNotNull(processingEvents.get(0).claimedAt());
		OutboxEvent processedEvent = processingEvents.get(0);
		repository.markSent(processedEvent.id(), processedEvent.claimToken(), claimAt);
		OutboxEvent claimedEvent = repository.findAll().stream()
				.filter(event -> event.id().equals(processedEvent.id()))
				.findFirst()
				.orElseThrow();
		assertNull(claimedEvent.claimedAt());
		assertEquals(claimAt, claimedEvent.sentAt());
		assertEquals(OutboxEventStatus.SENT, claimedEvent.status());

		processingEvents = repository.claimPendingReadyForDelivery(claimAt.plusMinutes(5), 1);
		assertNotNull(processingEvents.get(0).claimedAt());
		assertEquals(1, processingEvents.size());
		assertEquals("order-b", processingEvents.get(0).orderId());
	}

	@Test
	public void reschedulesFailedPendingEventForLaterRetry() {
		InMemoryOutboxEventRepository repository = new InMemoryOutboxEventRepository();
		repository.enqueueOrderPaid("retry-order-a");

		LocalDateTime claimAt = LocalDateTime.now().plusSeconds(1);
		LocalDateTime retryTime = claimAt.plusMinutes(5);
		List<OutboxEvent> processingEvents = repository.claimPendingReadyForDelivery(claimAt, 10);
		assertNotNull(processingEvents.get(0).claimedAt());
		Long eventId = processingEvents.get(0).id();
		repository.rescheduleAfterFailure(eventId, processingEvents.get(0).claimToken(), "SMS provider timeout",
				retryTime);
		OutboxEvent rescheduledEvent = repository.findAll().stream()
				.filter(event -> event.id().equals(eventId))
				.findFirst()
				.orElseThrow();
		assertNull(rescheduledEvent.claimedAt());
		OutboxEvent event = repository.findAll().get(0);
		assertEquals(OutboxEventStatus.PENDING, event.status());
		assertEquals(1, event.attemptCount());
		assertEquals(retryTime, event.nextAttemptAt());
		assertNull(event.sentAt());
		assertEquals("SMS provider timeout", event.lastError());

		// PENDING does not mean immediately deliverable: the future retry time delays
		// it.
		assertTrue(repository.claimPendingReadyForDelivery(claimAt, 10).isEmpty());
		assertEquals(1, repository.claimPendingReadyForDelivery(retryTime, 10).size());

	}

	@Test
	public void marksPendingEventFailedAndExcludesItFromReadyDelivery() {
		InMemoryOutboxEventRepository repository = new InMemoryOutboxEventRepository();
		repository.enqueueOrderPaid("retry-order-a");
		LocalDateTime claimAt = LocalDateTime.now().plusSeconds(1);
		List<OutboxEvent> processingEvents = repository.claimPendingReadyForDelivery(claimAt, 10);
		assertNotNull(processingEvents.get(0).claimedAt());
		Long eventId = processingEvents.get(0).id();
		repository.markFailed(eventId, processingEvents.get(0).claimToken(), "SMS provider unavailable");
		OutboxEvent failedEvent = repository.findAll().stream()
				.filter(event -> event.id().equals(eventId))
				.findFirst()
				.orElseThrow();
		assertNull(failedEvent.claimedAt());
		OutboxEvent event = repository.findAll().get(0);
		assertEquals(OutboxEventStatus.FAILED, event.status());
		assertEquals(1, event.attemptCount());
		assertEquals("SMS provider unavailable", event.lastError());
		assertNull(event.sentAt());
		// FAILED is a terminal audit state, so the worker never receives it again.
		assertEquals(0, repository.claimPendingReadyForDelivery(claimAt, 10).size());
	}

	@Test
	public void releasesExpiredProcessingEventForAnotherWorker() {
		InMemoryOutboxEventRepository repository = new InMemoryOutboxEventRepository();
		repository.enqueueOrderPaid("retry-order-a");
		LocalDateTime claimAt = LocalDateTime.now().plusSeconds(2);
		LocalDateTime claimedBefore = claimAt.plusSeconds(2);
		List<OutboxEvent> processingEvents = repository.claimPendingReadyForDelivery(claimAt, 10);
		assertEquals(1, processingEvents.size());
		assertEquals(OutboxEventStatus.PROCESSING, processingEvents.get(0).status());
		assertNotNull(processingEvents.get(0).claimedAt());
		assertEquals(0, processingEvents.get(0).attemptCount());

		int releasedEvents = repository.releaseExpiredClaims(claimedBefore);
		assertEquals(1, releasedEvents);
		OutboxEvent releasedEvent = repository.findAll().stream()
				.filter(event -> event.orderId().equals("retry-order-a"))
				.findFirst()
				.orElseThrow();
		assertEquals(OutboxEventStatus.PENDING, releasedEvent.status());
		assertNull(releasedEvent.claimedAt());
		assertEquals(0, releasedEvent.attemptCount());
		List<OutboxEvent> re_processingEvents = repository.claimPendingReadyForDelivery(claimedBefore, 10);
		assertEquals(1, re_processingEvents.size());
		assertEquals(OutboxEventStatus.PROCESSING, re_processingEvents.get(0).status());
		assertNotNull(re_processingEvents.get(0).claimedAt());
		assertEquals(0, re_processingEvents.get(0).attemptCount());
		assertEquals(processingEvents.get(0).id(), re_processingEvents.get(0).id());
	}

	@Test
	public void doesNotReleaseActiveProcessingEvent() {
		InMemoryOutboxEventRepository repository = new InMemoryOutboxEventRepository();
		repository.enqueueOrderPaid("retry-order-a");
		LocalDateTime claimAt = LocalDateTime.now().plusSeconds(2);
		LocalDateTime claimedBefore = claimAt.minusSeconds(2);
		List<OutboxEvent> processingEvents = repository.claimPendingReadyForDelivery(claimAt, 10);
		assertEquals(1, processingEvents.size());
		assertEquals(OutboxEventStatus.PROCESSING, processingEvents.get(0).status());
		assertNotNull(processingEvents.get(0).claimedAt());
		assertEquals(0, processingEvents.get(0).attemptCount());
		int releasedEvents = repository.releaseExpiredClaims(claimedBefore);
		assertEquals(0, releasedEvents);
		OutboxEvent releasedEvent = repository.findAll().stream()
				.filter(event -> event.orderId().equals("retry-order-a"))
				.findFirst()
				.orElseThrow();
		assertEquals(OutboxEventStatus.PROCESSING, releasedEvent.status());
		assertEquals(claimAt, releasedEvent.claimedAt());
		assertEquals(0, releasedEvent.attemptCount());
		List<OutboxEvent> noProcessingEvents = repository.claimPendingReadyForDelivery(claimAt, 10);
		assertEquals(0, noProcessingEvents.size());
	}

	@Test
	public void doesNotAllowStaleClaimTokenToUpdateReclaimedEvent() {
		InMemoryOutboxEventRepository repository = new InMemoryOutboxEventRepository();
		repository.enqueueOrderPaid("order-a");
		LocalDateTime claimAt = LocalDateTime.now();
		OutboxEvent workerAEvent = repository.claimPendingReadyForDelivery(claimAt, 1).get(0);
		int releasedEvents = repository.releaseExpiredClaims(claimAt.plusSeconds(2));
		assertEquals(1, releasedEvents);
		OutboxEvent workerBEvent = repository.claimPendingReadyForDelivery(claimAt.plusSeconds(8), 1).get(0);
		assertTrue(workerAEvent.claimToken() != null);
		assertTrue(workerBEvent.claimToken() != null);
		assertNotEquals(workerAEvent.claimToken(), workerBEvent.claimToken());

		repository.markSent(workerAEvent.id(), workerAEvent.claimToken(), claimAt.plusSeconds(10));
		OutboxEvent finalEvent = repository.findAll().stream()
				.filter(event -> event.orderId().equals("order-a"))
				.findFirst()
				.orElseThrow();

		assertEquals(OutboxEventStatus.PROCESSING, finalEvent.status());
		assertEquals(workerBEvent.claimToken(), finalEvent.claimToken());
		assertNull(finalEvent.sentAt());

		repository.markSent(workerBEvent.id(), workerBEvent.claimToken(), claimAt.plusSeconds(11));

		finalEvent = repository.findAll().stream()
				.filter(event -> event.orderId().equals("order-a"))
				.findFirst()
				.orElseThrow();

		assertEquals(OutboxEventStatus.SENT, finalEvent.status());
		assertNull(finalEvent.claimToken());
		assertNull(finalEvent.claimedAt());
	}
}
