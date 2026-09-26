package com.vishesh.orderengine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("postgres")
/* PostgreSQL contract tests: defaults, due-event selection, retry, success, and terminal failure. */
public class JdbcOutboxEventRepositoryTest {
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
        jdbcTemplate.update("Insert into orders (id,status) VALUES (?,?)", order.getId(), order.getStatus().name());
        outboxEventRepository.enqueueOrderPaid(savedOrderId);
        Map<String, Object> row = jdbcTemplate.queryForMap("SELECT * from outbox_events where order_id=?",
                savedOrderId);

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
        // This delete is inside the rollback-only test transaction, so existing rows are restored after it.
        jdbcTemplate.update("DELETE from outbox_events");
        savedOrderId = "jdbc-parent-order-test-" + UUID.randomUUID();
        Order order = new Order(savedOrderId, OrderStatus.CREATED);
        jdbcTemplate.update("Insert into orders (id,status) VALUES (?,?)", order.getId(), order.getStatus().name());
        jdbcTemplate.update("Insert into outbox_events (order_id,status,next_attempt_at) VALUES (?,?,?)", order.getId(),
                OutboxEventStatus.PENDING.name(), LocalDateTime.now().minusMinutes(5));

        jdbcTemplate.update("Insert into outbox_events (order_id,status,next_attempt_at) VALUES (?,?,?)", order.getId(),
                OutboxEventStatus.PENDING.name(), LocalDateTime.now().minusMinutes(3));
        jdbcTemplate.update("Insert into outbox_events (order_id,status,next_attempt_at) VALUES (?,?,?)", order.getId(),
                OutboxEventStatus.PENDING.name(), LocalDateTime.now().plusMinutes(3));
        List<OutboxEvent> events = outboxEventRepository.findPendingReadyForDelivery(LocalDateTime.now(), 10);

        assertEquals(2, events.size());
        assertTrue(events.get(0).nextAttemptAt().isBefore(events.get(1).nextAttemptAt()));
        LocalDateTime now = LocalDateTime.now().withNano(0);
        assertTrue(!events.get(0).nextAttemptAt().isAfter(now));
        assertTrue(!events.get(1).nextAttemptAt().isAfter(now));
    }

    @Test
    public void marksPendingEventSentInPostgres() {
        savedOrderId = "jdbc-parent-order-test-" + UUID.randomUUID();
        Order order = new Order(savedOrderId, OrderStatus.CREATED);
        jdbcTemplate.update("Insert into orders (id,status) VALUES (?,?)", order.getId(), order.getStatus().name());
        outboxEventRepository.enqueueOrderPaid(savedOrderId);
        List<OutboxEvent> events = jdbcTemplate.query("Select * from outbox_events where order_id=?",
                new OutboxEventRowMapper(), savedOrderId);

        LocalDateTime sentAt = LocalDateTime.now();
        outboxEventRepository.markSent(events.get(0).id(), sentAt);

        List<OutboxEvent> returnedEvents = jdbcTemplate.query("Select * from outbox_events where order_id=?",
                new OutboxEventRowMapper(), savedOrderId);

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
        jdbcTemplate.update("Insert into orders (id,status) VALUES (?,?)", order.getId(), order.getStatus().name());
        outboxEventRepository.enqueueOrderPaid(savedOrderId);
        List<OutboxEvent> events = jdbcTemplate.query("Select * from outbox_events where order_id=?",
                new OutboxEventRowMapper(), savedOrderId);
        LocalDateTime retryTime = LocalDateTime.now().withNano(0).plusMinutes(5);
        outboxEventRepository.rescheduleAfterFailure(events.get(0).id(), "SMS provider timeout", retryTime);
        List<OutboxEvent> returnedEvents = jdbcTemplate.query("Select * from outbox_events where order_id=?",
                new OutboxEventRowMapper(), savedOrderId);

        assertEquals(OutboxEventStatus.PENDING, returnedEvents.get(0).status());
        assertEquals(1, returnedEvents.get(0).attemptCount());
        assertEquals("SMS provider timeout", returnedEvents.get(0).lastError());
        assertNull(returnedEvents.get(0).sentAt());
        assertEquals(retryTime, returnedEvents.get(0).nextAttemptAt());
        // The persisted future timestamp keeps this PENDING event out of the current worker batch.
        assertEquals(0, outboxEventRepository.findPendingReadyForDelivery(LocalDateTime.now(), 10).size());
        assertEquals(1, outboxEventRepository.findPendingReadyForDelivery(retryTime.plusMinutes(1), 10).size());
        assertEquals(events.get(0).id(),
                outboxEventRepository.findPendingReadyForDelivery(retryTime.plusMinutes(1), 10).get(0).id());

    }

    @Test
    @Transactional
    public void marksPendingEventFailedInPostgres() {
        jdbcTemplate.update("DELETE from outbox_events");
        savedOrderId = "jdbc-parent-order-test-" + UUID.randomUUID();
        Order order = new Order(savedOrderId, OrderStatus.CREATED);
        jdbcTemplate.update("Insert into orders (id,status) VALUES (?,?)", order.getId(), order.getStatus().name());
        outboxEventRepository.enqueueOrderPaid(savedOrderId);
        List<OutboxEvent> events = jdbcTemplate.query("Select * from outbox_events where order_id=?",
                new OutboxEventRowMapper(), savedOrderId);
        outboxEventRepository.markFailed(events.get(0).id(), "SMS provider unavailable");
        List<OutboxEvent> returnedEvents = jdbcTemplate.query("Select * from outbox_events where order_id=?",
                new OutboxEventRowMapper(), savedOrderId);

        assertEquals(OutboxEventStatus.FAILED, returnedEvents.get(0).status());
        assertEquals(1, returnedEvents.get(0).attemptCount());
        assertEquals("SMS provider unavailable", returnedEvents.get(0).lastError());
        assertNull(returnedEvents.get(0).sentAt());
        assertEquals(0, outboxEventRepository.findPendingReadyForDelivery(LocalDateTime.now(), 10).size());
    }
}
