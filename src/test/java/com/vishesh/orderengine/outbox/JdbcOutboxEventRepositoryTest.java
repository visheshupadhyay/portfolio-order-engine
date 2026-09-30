package com.vishesh.orderengine.outbox;

import com.vishesh.orderengine.integration.AbstractPostgresIntegrationTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import com.vishesh.orderengine.order.Order;
import com.vishesh.orderengine.order.OrderStatus;

@SpringBootTest
@ActiveProfiles("postgres")
/*
 * PostgreSQL contract tests: defaults, due-event selection, retry, success, and
 * terminal failure.
 */
/*
 * PostgreSQL contract tests: they prove database-level claiming is safe across
 * concurrent threads, unlike an in-JVM synchronized-only solution.
 */
public class JdbcOutboxEventRepositoryTest extends AbstractPostgresIntegrationTest {
	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private OutboxEventRepository outboxEventRepository;

	private String savedOrderId;

	@AfterEach
	public void cleanUpDatabase() {
		if (savedOrderId != null) {
			// Child rows must be removed first because outbox_events references orders.
			jdbcTemplate.update("DELETE FROM outbox_events WHERE order_id = ?", savedOrderId);
			jdbcTemplate.update("DELETE FROM orders WHERE id = ?", savedOrderId);

		}

	}

	@Test
	public void enqueuesPendingOrderPaidEventInPostgres() {
		savedOrderId = "jdbc-outbox-test-" + UUID.randomUUID();
		Order order = new Order(savedOrderId, OrderStatus.CREATED);
		assertInstanceOf(JdbcOutboxEventRepository.class, outboxEventRepository);
		jdbcTemplate.update("Insert into orders (id,status) VALUES (?,?)", order.getId(),
				order.getStatus().name());
		outboxEventRepository.enqueueOrderPaid(savedOrderId);
		Map<String, Object> row = jdbcTemplate.queryForMap("SELECT * from outbox_events where order_id=?",
				savedOrderId);
		assertNull(row.get("claimed_at"));
		assertEquals(savedOrderId, row.get("order_id"));
		assertEquals("ORDER_PAID", row.get("event_type"));
		assertEquals(OutboxEventStatus.PENDING.name(), row.get("status"));
		assertEquals(0, row.get("attempt_count"));
		assertNull(row.get("sent_at"));
		assertNull(row.get("last_error"));

	}

	@Test
	@Transactional
	public void returnsOnlyPendingEventsWhoseRetryTimeHasArrived() {
		// This delete is inside the rollback-only test transaction, so existing rows
		// are restored after it.
		jdbcTemplate.update("DELETE from outbox_events");
		savedOrderId = "jdbc-parent-order-test-" + UUID.randomUUID();
		Order order = new Order(savedOrderId, OrderStatus.CREATED);
		jdbcTemplate.update("Insert into orders (id,status) VALUES (?,?)", order.getId(),
				order.getStatus().name());
		jdbcTemplate.update("Insert into outbox_events (order_id,status,next_attempt_at) VALUES (?,?,?)",
				order.getId(),
				OutboxEventStatus.PENDING.name(), LocalDateTime.now().minusMinutes(5));

		jdbcTemplate.update("Insert into outbox_events (order_id,status,next_attempt_at) VALUES (?,?,?)",
				order.getId(),
				OutboxEventStatus.PENDING.name(), LocalDateTime.now().minusMinutes(3));
		jdbcTemplate.update("Insert into outbox_events (order_id,status,next_attempt_at) VALUES (?,?,?)",
				order.getId(),
				OutboxEventStatus.PENDING.name(), LocalDateTime.now().plusMinutes(3));
		List<OutboxEvent> events = outboxEventRepository.claimPendingReadyForDelivery(LocalDateTime.now(), 10);
		assertEquals(2, events.size());
		assertNotNull(events.get(0).claimedAt());
		assertNotNull(events.get(1).claimedAt());

		assertTrue(events.get(0).nextAttemptAt().isBefore(events.get(1).nextAttemptAt()));
		LocalDateTime now = LocalDateTime.now().withNano(0);
		assertTrue(!events.get(0).nextAttemptAt().isAfter(now));
		assertTrue(!events.get(1).nextAttemptAt().isAfter(now));
		assertTrue(events.get(0).status() == OutboxEventStatus.PROCESSING);
		assertTrue(events.get(1).status() == OutboxEventStatus.PROCESSING);
	}

	@Test
	public void marksPendingEventSentInPostgres() {
		savedOrderId = "jdbc-parent-order-test-" + UUID.randomUUID();
		Order order = new Order(savedOrderId, OrderStatus.CREATED);
		jdbcTemplate.update("Insert into orders (id,status) VALUES (?,?)", order.getId(),
				order.getStatus().name());
		outboxEventRepository.enqueueOrderPaid(savedOrderId);

		LocalDateTime sentAt = LocalDateTime.now();
		List<OutboxEvent> events = outboxEventRepository.claimPendingReadyForDelivery(sentAt.plusSeconds(5), 1);
		assertNotNull(events.get(0).claimedAt());
		outboxEventRepository.markSent(events.get(0).id(), events.get(0).claimToken(), sentAt);

		List<OutboxEvent> returnedEvents = jdbcTemplate.query("Select * from outbox_events where order_id=?",
				new OutboxEventRowMapper(), savedOrderId);
		assertNull(returnedEvents.get(0).claimedAt());

		assertEquals(OutboxEventStatus.SENT, returnedEvents.get(0).status());
		assertNotNull(returnedEvents.get(0).sentAt());
		assertNull(returnedEvents.get(0).lastError());
	}

	@Test
	@Transactional
	public void reschedulesFailedPendingEventForLaterRetryInPostgres() {
		jdbcTemplate.update("DELETE from outbox_events");
		savedOrderId = "jdbc-parent-order-test-" + UUID.randomUUID();
		Order order = new Order(savedOrderId, OrderStatus.CREATED);
		jdbcTemplate.update("Insert into orders (id,status) VALUES (?,?)", order.getId(),
				order.getStatus().name());
		outboxEventRepository.enqueueOrderPaid(savedOrderId);

		List<OutboxEvent> events = outboxEventRepository
				.claimPendingReadyForDelivery(LocalDateTime.now().plusSeconds(5), 10);
		assertNotNull(events.get(0).claimedAt());
		LocalDateTime retryTime = LocalDateTime.now().withNano(0).plusMinutes(10);
		outboxEventRepository.rescheduleAfterFailure(events.get(0).id(), events.get(0).claimToken(),
				"SMS provider timeout", retryTime);
		List<OutboxEvent> returnedEvents = jdbcTemplate.query("Select * from outbox_events where order_id=?",
				new OutboxEventRowMapper(), savedOrderId);
		assertNull(returnedEvents.get(0).claimedAt());
		assertEquals(OutboxEventStatus.PENDING, returnedEvents.get(0).status());
		assertEquals(1, returnedEvents.get(0).attemptCount());
		assertEquals("SMS provider timeout", returnedEvents.get(0).lastError());
		assertNull(returnedEvents.get(0).sentAt());
		assertEquals(retryTime, returnedEvents.get(0).nextAttemptAt());
		// The persisted future timestamp keeps this PENDING event out of the current
		// worker batch.
		assertEquals(0, outboxEventRepository.claimPendingReadyForDelivery(LocalDateTime.now(), 10).size());

		List<OutboxEvent> retryEvents = outboxEventRepository
				.claimPendingReadyForDelivery(retryTime.plusMinutes(1), 10);
		assertNotNull(retryEvents.get(0).claimedAt());
		assertEquals(1, retryEvents.size());
		assertEquals(returnedEvents.get(0).id(), retryEvents.get(0).id());
	}

	@Test
	@Transactional
	public void marksPendingEventFailedInPostgres() {
		jdbcTemplate.update("DELETE from outbox_events");
		savedOrderId = "jdbc-parent-order-test-" + UUID.randomUUID();
		Order order = new Order(savedOrderId, OrderStatus.CREATED);
		jdbcTemplate.update("Insert into orders (id,status) VALUES (?,?)", order.getId(),
				order.getStatus().name());
		outboxEventRepository.enqueueOrderPaid(savedOrderId);
		List<OutboxEvent> events = outboxEventRepository
				.claimPendingReadyForDelivery(LocalDateTime.now().plusSeconds(7), 10);
		assertNotNull(events.get(0).claimedAt());
		jdbcTemplate.update("Update outbox_events set attempt_count=2 where order_id=?", savedOrderId);
		outboxEventRepository.markFailed(events.get(0).id(), events.get(0).claimToken(), "SMS provider unavailable");
		List<OutboxEvent> returnedEvents = jdbcTemplate.query("Select * from outbox_events where order_id=?",
				new OutboxEventRowMapper(), savedOrderId);
		assertNull(returnedEvents.get(0).claimedAt());
		assertEquals(OutboxEventStatus.FAILED, returnedEvents.get(0).status());
		assertEquals(3, returnedEvents.get(0).attemptCount());
		assertEquals("SMS provider unavailable", returnedEvents.get(0).lastError());
		assertNull(returnedEvents.get(0).sentAt());
		assertEquals(0, outboxEventRepository.claimPendingReadyForDelivery(LocalDateTime.now(), 10).size());
	}

	@Test
	public void allowsOnlyOneConcurrentWorkerToClaimTheSameEvent() throws Exception {
		savedOrderId = "jdbc-parent-order-test-" + UUID.randomUUID();
		Order order = new Order(savedOrderId, OrderStatus.CREATED);
		jdbcTemplate.update("Insert into orders (id,status) VALUES (?,?)", order.getId(),
				order.getStatus().name());
		outboxEventRepository.enqueueOrderPaid(savedOrderId);

		LocalDateTime claimTime = LocalDateTime.now().plusSeconds(5);
		ExecutorService executorService = Executors.newFixedThreadPool(2);
		CountDownLatch workersReady = new CountDownLatch(2);
		CountDownLatch startWorkers = new CountDownLatch(1);

		try {
			Future<List<OutboxEvent>> workerA = executorService.submit(() -> {
				workersReady.countDown();
				startWorkers.await();
				return outboxEventRepository.claimPendingReadyForDelivery(claimTime, 1);
			});

			Future<List<OutboxEvent>> workerB = executorService.submit(() -> {
				workersReady.countDown();
				startWorkers.await();
				return outboxEventRepository.claimPendingReadyForDelivery(claimTime, 1);
			});

			workersReady.await();

			startWorkers.countDown();

			List<OutboxEvent> firstClaim = workerA.get();
			List<OutboxEvent> secondClaim = workerB.get();

			assertEquals(1, firstClaim.size() + secondClaim.size());
			if (firstClaim.size() > 0) {
				assertEquals(OutboxEventStatus.PROCESSING, firstClaim.get(0).status());
				assertEquals(savedOrderId, firstClaim.get(0).orderId());
				assertNotNull(firstClaim.get(0).claimedAt());
				assertEquals(0, secondClaim.size());
			} else {
				assertEquals(OutboxEventStatus.PROCESSING, secondClaim.get(0).status());
				assertEquals(savedOrderId, secondClaim.get(0).orderId());
				assertNotNull(secondClaim.get(0).claimedAt());
				assertEquals(0, firstClaim.size());
			}
			assertEquals(0, outboxEventRepository.claimPendingReadyForDelivery(LocalDateTime.now(), 1).size());

		} finally {
			executorService.shutdown();
		}
	}

	@Test
	public void releasesExpiredProcessingEventForAnotherWorker() {
		savedOrderId = "retry-order-a-" + UUID.randomUUID();
		Order order = new Order(savedOrderId, OrderStatus.CREATED);
		jdbcTemplate.update("Insert into orders (id,status) VALUES (?,?)", order.getId(),
				order.getStatus().name());
		outboxEventRepository.enqueueOrderPaid(savedOrderId);

		LocalDateTime claimAt = LocalDateTime.now().plusSeconds(2);
		LocalDateTime claimedBefore = claimAt.plusSeconds(2);
		List<OutboxEvent> processingEvents = outboxEventRepository.claimPendingReadyForDelivery(claimAt, 10);
		assertEquals(1, processingEvents.size());
		assertEquals(OutboxEventStatus.PROCESSING, processingEvents.get(0).status());
		assertNotNull(processingEvents.get(0).claimedAt());
		assertEquals(0, processingEvents.get(0).attemptCount());

		int releasedEventCount = outboxEventRepository.releaseExpiredClaims(claimedBefore);
		assertEquals(1, releasedEventCount);
		List<OutboxEvent> releasedEvents = jdbcTemplate.query("Select * from outbox_events where order_id=?",
				new OutboxEventRowMapper(), savedOrderId);
		assertEquals(OutboxEventStatus.PENDING, releasedEvents.get(0).status());
		assertNull(releasedEvents.get(0).claimedAt());
		assertEquals(0, releasedEvents.get(0).attemptCount());
		List<OutboxEvent> re_processingEvents = outboxEventRepository.claimPendingReadyForDelivery(claimedBefore, 10);
		assertEquals(1, re_processingEvents.size());
		assertEquals(OutboxEventStatus.PROCESSING, re_processingEvents.get(0).status());
		assertNotNull(re_processingEvents.get(0).claimedAt());
		assertEquals(0, re_processingEvents.get(0).attemptCount());
		assertEquals(processingEvents.get(0).id(), re_processingEvents.get(0).id());
	}

	@Test
	public void doesNotReleaseActiveProcessingEvent() {
		savedOrderId = "order-a-" + UUID.randomUUID();
		Order order = new Order(savedOrderId, OrderStatus.CREATED);
		jdbcTemplate.update("Insert into orders (id,status) VALUES (?,?)", order.getId(), order.getStatus().name());
		outboxEventRepository.enqueueOrderPaid(savedOrderId);
		LocalDateTime claimAt = LocalDateTime.now().withNano(0).plusSeconds(5);
		LocalDateTime claimedBefore = claimAt.minusSeconds(2);
		List<OutboxEvent> processingEvents = outboxEventRepository.claimPendingReadyForDelivery(claimAt, 10);
		assertEquals(1, processingEvents.size());
		assertEquals(OutboxEventStatus.PROCESSING, processingEvents.get(0).status());
		assertNotNull(processingEvents.get(0).claimedAt());
		assertEquals(0, processingEvents.get(0).attemptCount());
		int releasedEvents = outboxEventRepository.releaseExpiredClaims(claimedBefore);
		assertEquals(0, releasedEvents);
		List<OutboxEvent> nonReleasedEvents = jdbcTemplate.query("Select * from outbox_events where order_id=?",
				new OutboxEventRowMapper(), savedOrderId);
		assertEquals(1, nonReleasedEvents.size());
		assertEquals(OutboxEventStatus.PROCESSING, nonReleasedEvents.get(0).status());
		assertEquals(claimAt, nonReleasedEvents.get(0).claimedAt());
		assertEquals(0, nonReleasedEvents.get(0).attemptCount());
		List<OutboxEvent> noProcessingEvents = outboxEventRepository.claimPendingReadyForDelivery(claimAt, 10);
		assertEquals(0, noProcessingEvents.size());
	}

	@Test
	public void doesNotAllowStaleClaimTokenToUpdateReclaimedEvent() {
		savedOrderId = "order-a-" + UUID.randomUUID();
		Order order = new Order(savedOrderId, OrderStatus.CREATED);
		jdbcTemplate.update("Insert into orders (id,status) VALUES (?,?)", order.getId(), order.getStatus().name());
		outboxEventRepository.enqueueOrderPaid(savedOrderId);
		LocalDateTime claimAt = LocalDateTime.now();
		OutboxEvent workerAEvent = outboxEventRepository.claimPendingReadyForDelivery(claimAt.plusSeconds(3), 1).get(0);
		int releasedEvents = outboxEventRepository.releaseExpiredClaims(claimAt.plusSeconds(5));
		assertEquals(1, releasedEvents);
		OutboxEvent workerBEvent = outboxEventRepository.claimPendingReadyForDelivery(claimAt.plusSeconds(8), 1).get(0);
		assertTrue(workerAEvent.claimToken() != null);
		assertTrue(workerBEvent.claimToken() != null);
		assertNotEquals(workerAEvent.claimToken(), workerBEvent.claimToken());

		outboxEventRepository.markSent(workerAEvent.id(), workerAEvent.claimToken(), claimAt.plusSeconds(10));
		OutboxEvent finalEvent = jdbcTemplate.query("Select * from outbox_events where order_id=?",
				new OutboxEventRowMapper(), savedOrderId).stream()
				.filter(event -> event.orderId().equals(savedOrderId))
				.findFirst()
				.orElseThrow();

		assertEquals(OutboxEventStatus.PROCESSING, finalEvent.status());
		assertEquals(workerBEvent.claimToken(), finalEvent.claimToken());
		assertNull(finalEvent.sentAt());

		outboxEventRepository.markSent(workerBEvent.id(), workerBEvent.claimToken(), claimAt.plusSeconds(11));

		finalEvent = jdbcTemplate.query("Select * from outbox_events where order_id=?",
				new OutboxEventRowMapper(), savedOrderId).stream()
				.filter(event -> event.orderId().equals(savedOrderId))
				.findFirst()
				.orElseThrow();

		assertEquals(OutboxEventStatus.SENT, finalEvent.status());
		assertNull(finalEvent.claimToken());
		assertNull(finalEvent.claimedAt());
	}
}
