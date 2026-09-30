package com.vishesh.orderengine.outbox;

import com.vishesh.orderengine.integration.AbstractPostgresIntegrationTest;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.vishesh.orderengine.notification.AbstractNotifier;
import com.vishesh.orderengine.order.Order;
import com.vishesh.orderengine.notification.OrderPaidNotificationService;
import com.vishesh.orderengine.order.OrderStatus;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@SpringBootTest
@ActiveProfiles("postgres")
/* End-to-end worker tests against persisted PostgreSQL outbox rows. */
/*
 * End-to-end PostgreSQL proof that real persisted rows follow the same worker
 * and
 * lease-recovery lifecycle as the in-memory tests.
 */
public class PostgresOutboxEventDeliveryWorkerIntegrationTest extends AbstractPostgresIntegrationTest {
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private OutboxEventDeliveryWorker outboxEventDeliveryWorker;
    @Autowired
    private OutboxEventRepository outboxEventRepository;
    private String savedOrderId;

    private final OutboxDeliveryProperties deliveryProperties = new OutboxDeliveryProperties(
            false,
            Duration.ofSeconds(5),
            100,
            Duration.ofMinutes(1),
            Duration.ofMinutes(5),
            3);

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
        OutboxEvent event = jdbcTemplate
                .query("Select * from outbox_events where order_id =?", new OutboxEventRowMapper(), savedOrderId)
                .get(0);
        assertEquals(OutboxEventStatus.SENT, event.status());
        assertEquals(now, event.sentAt());
        assertNull(event.lastError());
    }

    @Test
    public void reschedulesPendingOutboxEventWhenPostgresWorkerDeliveryFails() {
        PostgresTestingNotifier notifier = new PostgresTestingNotifier("TEST");
        OrderPaidNotificationService orderPaidNotificationService = new OrderPaidNotificationService(notifier);
        // Use the real JDBC repository but a deterministic failing notifier.
        OutboxEventDeliveryWorker outboxEventDeliveryWorker = new OutboxEventDeliveryWorker(
                outboxEventRepository,
                orderPaidNotificationService,
                deliveryProperties,
                new OutboxDeliveryMetrics(new SimpleMeterRegistry()));
        savedOrderId = "postgres-outbox-delivery-test-" + UUID.randomUUID();
        jdbcTemplate.update("INSERT into orders (id, status) VALUES (?,?)", savedOrderId, OrderStatus.CREATED.name());
        jdbcTemplate.update("INSERT into outbox_events (order_id) VALUES (?)", savedOrderId);
        LocalDateTime now = LocalDateTime.now().plusMinutes(3).withNano(0);
        assertDoesNotThrow(() -> outboxEventDeliveryWorker.deliverReadyEvents(now, 10));
        OutboxEvent event = jdbcTemplate
                .query("Select * from outbox_events where order_id =?", new OutboxEventRowMapper(), savedOrderId)
                .get(0);
        assertEquals(OutboxEventStatus.PENDING, event.status());
        assertEquals(1, event.attemptCount());
        assertEquals("SMS provider unavailable", event.lastError());
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

        OutboxEvent passedEvent = jdbcTemplate
                .query("Select * from outbox_events where order_id =?", new OutboxEventRowMapper(), savedOrderId)
                .get(0);
        assertEquals(OutboxEventStatus.SENT, passedEvent.status());
        assertEquals(workerRunAt, passedEvent.sentAt());
        assertNull(passedEvent.claimToken());
        assertNull(passedEvent.claimedAt());
        assertNull(passedEvent.lastError());
        assertEquals(0, passedEvent.attemptCount());
    }

    @Test
    public void doesNotSendDuplicateNotificationWhenRecoveredEventIsRetried() {
        savedOrderId = "postgres-outbox-delivery-test-" + UUID.randomUUID();
        jdbcTemplate.update("INSERT into orders (id, status) VALUES (?,?)", savedOrderId, OrderStatus.CREATED.name());
        jdbcTemplate.update("INSERT into outbox_events (order_id) VALUES (?)", savedOrderId);

        PostgresTestNotifier postgresTestNotifier = new PostgresTestNotifier("Success");
        OrderPaidNotificationService orderPaidNotificationService = new OrderPaidNotificationService(
                postgresTestNotifier);
        OutboxEventDeliveryWorker outboxEventDeliveryWorker = new OutboxEventDeliveryWorker(
                outboxEventRepository, orderPaidNotificationService, deliveryProperties,
                new OutboxDeliveryMetrics(new SimpleMeterRegistry()));

        LocalDateTime firstClaimAt = LocalDateTime.now().withNano(0);
        LocalDateTime workerRunAt = firstClaimAt.plusMinutes(6);
        OutboxEvent firstClaim = outboxEventRepository.claimPendingReadyForDelivery(firstClaimAt.plusSeconds(3), 1)
                .get(0);

        assertEquals(OutboxEventStatus.PROCESSING, firstClaim.status());

        orderPaidNotificationService.notifyOrderPaid(new Order(savedOrderId), firstClaim.id());
        assertEquals(1, postgresTestNotifier.getSendCount());
        assertEquals(String.valueOf(firstClaim.id()), postgresTestNotifier.getReceivedIdempotencyKey());

        outboxEventDeliveryWorker.deliverReadyEvents(workerRunAt, 1);

        assertEquals(1, postgresTestNotifier.getSendCount());
        OutboxEvent passedEvent = jdbcTemplate
                .query("Select * from outbox_events where order_id =?", new OutboxEventRowMapper(), savedOrderId)
                .get(0);
        assertEquals(firstClaim.id(), passedEvent.id());
        assertEquals(OutboxEventStatus.SENT, passedEvent.status());
        assertEquals(workerRunAt, passedEvent.sentAt());
        assertEquals(0, passedEvent.attemptCount());
        assertEquals(String.valueOf(passedEvent.id()), postgresTestNotifier.getReceivedIdempotencyKey());

    }
}

class PostgresTestingNotifier extends AbstractNotifier {

    public PostgresTestingNotifier(String senderName) {
        super(senderName);

    }

    @Override
    protected void deliver(String message) {
        throw new RuntimeException("SMS provider unavailable");
    }

}

class PostgresTestNotifier extends AbstractNotifier {
    private String message;
    private int sendCount;
    private String receivedIdempotencyKey;

    public PostgresTestNotifier(String senderName) {
        super(senderName);
    }

    @Override
    protected void deliver(String message) {
        this.message = message;
        sendCount++;
    }

    @Override
    protected void deliver(String message, String idempotencyKey) {
        this.message = message;
        this.receivedIdempotencyKey = idempotencyKey;

        sendCount++;
    }

    public String getMessage() {
        return this.message;
    }

    public int getSendCount() {
        return this.sendCount;
    }

    public String getReceivedIdempotencyKey() {
        return this.receivedIdempotencyKey;
    }
}
