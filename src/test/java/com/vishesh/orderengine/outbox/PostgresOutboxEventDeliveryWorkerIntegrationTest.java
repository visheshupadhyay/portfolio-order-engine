package com.vishesh.orderengine.outbox;

import com.vishesh.orderengine.integration.AbstractPostgresIntegrationTest;
import com.vishesh.orderengine.message.OrderPaidEventPublisher;
import com.vishesh.orderengine.message.OrderPaidMessage;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.vishesh.orderengine.order.OrderStatus;

@SpringBootTest
@ActiveProfiles("postgres")
/* End-to-end worker tests against persisted PostgreSQL outbox rows. */
/*
 * End-to-end PostgreSQL proof that real persisted rows follow the same worker
 * and
 * lease-recovery lifecycle as the in-memory tests.
 */
/*
 * Real PostgreSQL proof of the outbox-to-publisher hand-off. The database rows,
 * claiming, recovery, and retry state are real; the publisher is replaced so
 * this test focuses on durable outbox behaviour rather than broker networking.
 */
public class PostgresOutboxEventDeliveryWorkerIntegrationTest extends AbstractPostgresIntegrationTest {
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private OutboxEventDeliveryWorker outboxEventDeliveryWorker;
    @Autowired
    private OutboxEventRepository outboxEventRepository;
    @MockitoBean
    private OrderPaidEventPublisher publisher;

    private String savedOrderId;

    @AfterEach
    public void cleanUpDatabase() {
        if (savedOrderId != null) {
            jdbcTemplate.update("DELETE FROM outbox_events WHERE order_id = ?", savedOrderId);
            jdbcTemplate.update("DELETE FROM orders WHERE id = ?", savedOrderId);
        }
    }

    @Test
    public void deliversPendingOutboxEventThroughPostgresWorker() {
        savedOrderId = "postgres-outbox-delivery-test-" + UUID.randomUUID();
        jdbcTemplate.update("INSERT into orders (id, status) VALUES (?,?)", savedOrderId, OrderStatus.CREATED.name());
        jdbcTemplate.update("INSERT into outbox_events (order_id) VALUES (?)", savedOrderId);
        // Whole-second time avoids PostgreSQL microsecond precision differences in
        // exact assertions.
        LocalDateTime now = LocalDateTime.now().plusMinutes(3).withNano(0);
        outboxEventDeliveryWorker.deliverReadyEvents(now, 10);
        ArgumentCaptor<OrderPaidMessage> messageCaptor = ArgumentCaptor.forClass(OrderPaidMessage.class);
        verify(publisher).publish(messageCaptor.capture());
        OrderPaidMessage publishedMessage = messageCaptor.getValue();
        assertEquals(publishedMessage.orderId(), savedOrderId);
        OutboxEvent event = jdbcTemplate
                .query("Select * from outbox_events where order_id =?", new OutboxEventRowMapper(), savedOrderId)
                .get(0);
        assertEquals(publishedMessage.eventId(), event.id());

        assertEquals(OutboxEventStatus.SENT, event.status());
        assertEquals(now, event.sentAt());
        assertNull(event.lastError());
    }

    @Test
    public void reschedulesPendingOutboxEventWhenPostgresWorkerDeliveryFails() {
        savedOrderId = "postgres-outbox-delivery-test-" + UUID.randomUUID();
        jdbcTemplate.update("INSERT into orders (id, status) VALUES (?,?)", savedOrderId, OrderStatus.CREATED.name());
        jdbcTemplate.update("INSERT into outbox_events (order_id) VALUES (?)", savedOrderId);
        LocalDateTime now = LocalDateTime.now().plusMinutes(3).withNano(0);
        doThrow(new RuntimeException("Kafka unavailable")).when(publisher)
                .publish(any(OrderPaidMessage.class));

        assertDoesNotThrow(() -> outboxEventDeliveryWorker.deliverReadyEvents(now, 10));

        OutboxEvent event = jdbcTemplate
                .query("Select * from outbox_events where order_id =?", new OutboxEventRowMapper(), savedOrderId)
                .get(0);
        assertEquals(OutboxEventStatus.PENDING, event.status());
        assertEquals(1, event.attemptCount());
        assertEquals("Kafka unavailable", event.lastError());
        assertNull(event.sentAt());
        assertEquals(now.plusMinutes(1), event.nextAttemptAt());
    }

    @Test
    public void releasesExpiredClaimAndDeliversEventDuringWorkerRun() {
        savedOrderId = "postgres-outbox-delivery-test-" + UUID.randomUUID();
        jdbcTemplate.update("INSERT into orders (id, status) VALUES (?,?)", savedOrderId, OrderStatus.CREATED.name());
        jdbcTemplate.update("INSERT into outbox_events (order_id) VALUES (?)", savedOrderId);

        LocalDateTime workerRunAt = LocalDateTime.now().withNano(0).plusMinutes(10);
        LocalDateTime oldClaimAt = workerRunAt.minusMinutes(6);
        OutboxEvent manualEvent = outboxEventRepository.claimPendingReadyForDelivery(oldClaimAt, 1).get(0);
        assertNotNull(manualEvent);
        assertEquals(OutboxEventStatus.PROCESSING, manualEvent.status());
        assertNotNull(manualEvent.claimToken());
        outboxEventDeliveryWorker.deliverReadyEvents(workerRunAt, 10);

        ArgumentCaptor<OrderPaidMessage> messageCaptor = ArgumentCaptor.forClass(OrderPaidMessage.class);
        verify(publisher).publish(messageCaptor.capture());
        OrderPaidMessage publishedMessage = messageCaptor.getValue();
        assertEquals(publishedMessage.orderId(), savedOrderId);

        OutboxEvent passedEvent = jdbcTemplate
                .query("Select * from outbox_events where order_id =?", new OutboxEventRowMapper(), savedOrderId)
                .get(0);

        assertEquals(publishedMessage.eventId(), passedEvent.id());
        assertEquals(OutboxEventStatus.SENT, passedEvent.status());
        assertEquals(workerRunAt, passedEvent.sentAt());
        assertNull(passedEvent.claimToken());
        assertNull(passedEvent.claimedAt());
        assertNull(passedEvent.lastError());
        assertEquals(0, passedEvent.attemptCount());
    }

    @Test
    public void republishesRecoveredPostgresEventAfterPossibleCrashBeforeMarkingSent() {
        savedOrderId = "postgres-outbox-delivery-test-" + UUID.randomUUID();
        jdbcTemplate.update("INSERT into orders (id, status) VALUES (?,?)", savedOrderId, OrderStatus.CREATED.name());
        jdbcTemplate.update("INSERT into outbox_events (order_id) VALUES (?)", savedOrderId);

        LocalDateTime firstClaimAt = LocalDateTime.now().withNano(0);
        LocalDateTime workerRunAt = firstClaimAt.plusMinutes(6);
        OutboxEvent firstClaim = outboxEventRepository.claimPendingReadyForDelivery(firstClaimAt.plusSeconds(3), 1)
                .get(0);
        publisher.publish(new OrderPaidMessage(firstClaim.id(), savedOrderId));
        assertEquals(OutboxEventStatus.PROCESSING, firstClaim.status());

        outboxEventDeliveryWorker.deliverReadyEvents(workerRunAt, 1);
        ArgumentCaptor<OrderPaidMessage> messageCaptor = ArgumentCaptor.forClass(OrderPaidMessage.class);

        verify(publisher, times(2)).publish(messageCaptor.capture());

        List<OrderPaidMessage> publishedMessages = messageCaptor.getAllValues();

        assertEquals(2, publishedMessages.size());
        OutboxEvent passedEvent = jdbcTemplate
                .query("Select * from outbox_events where order_id =?", new OutboxEventRowMapper(), savedOrderId)
                .get(0);
        assertEquals(firstClaim.id(), passedEvent.id());
        assertEquals(OutboxEventStatus.SENT, passedEvent.status());
        assertEquals(workerRunAt, passedEvent.sentAt());
        assertEquals(0, passedEvent.attemptCount());
        assertEquals(firstClaim.id(), publishedMessages.get(0).eventId());
        assertEquals(savedOrderId, publishedMessages.get(0).orderId());
        assertEquals(firstClaim.id(), publishedMessages.get(1).eventId());
        assertEquals(savedOrderId, publishedMessages.get(1).orderId());
    }
}
