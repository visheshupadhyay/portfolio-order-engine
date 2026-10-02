package com.vishesh.orderengine.outbox;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.vishesh.orderengine.integration.AbstractPostgresIntegrationTest;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.vishesh.orderengine.order.Order;
import com.vishesh.orderengine.notification.OrderPaidNotificationService;
import com.vishesh.orderengine.order.OrderStatus;

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
    @Autowired
    private OrderPaidNotificationService orderPaidNotificationService;

    private String savedOrderId;

    private static final WireMockServer smsProvider = new WireMockServer(wireMockConfig().dynamicPort());

    static {
        smsProvider.start();
    }

    @DynamicPropertySource
    static void configureSmsProvider(DynamicPropertyRegistry registry) {
        registry.add("notification.sms.provider-base-url", smsProvider::baseUrl);
    }

    @AfterAll
    static void stopSmsProvider() {
        smsProvider.stop();
    }

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
        smsProvider.resetAll();
    }

    @Test
    public void deliversPendingOutboxEventThroughPostgresWorker() {
        savedOrderId = "postgres-outbox-delivery-test-" + UUID.randomUUID();
        jdbcTemplate.update("INSERT into orders (id, status) VALUES (?,?)", savedOrderId, OrderStatus.CREATED.name());
        jdbcTemplate.update("INSERT into outbox_events (order_id) VALUES (?)", savedOrderId);
        // Whole-second time avoids PostgreSQL microsecond precision differences in
        // exact assertions.
        smsProvider.stubFor(post(urlEqualTo("/sms")).willReturn(aResponse().withStatus(202)));
        LocalDateTime now = LocalDateTime.now().plusMinutes(3).withNano(0);
        outboxEventDeliveryWorker.deliverReadyEvents(now, 10);
        OutboxEvent event = jdbcTemplate
                .query("Select * from outbox_events where order_id =?", new OutboxEventRowMapper(), savedOrderId)
                .get(0);
        assertEquals(OutboxEventStatus.SENT, event.status());
        assertEquals(now, event.sentAt());
        assertNull(event.lastError());

        smsProvider.verify(1, postRequestedFor(urlEqualTo("/sms"))
                .withHeader("Content-Type", equalTo("application/json"))
                .withHeader("Idempotency-Key", equalTo(String.valueOf(event.id())))
                .withRequestBody(matchingJsonPath("$.senderName", equalTo("OrderEngine")))
                .withRequestBody(matchingJsonPath("$.message", equalTo("Order is paid orderID:" + savedOrderId))));
    }

    @Test
    public void reschedulesPendingOutboxEventWhenPostgresWorkerDeliveryFails() {
        savedOrderId = "postgres-outbox-delivery-test-" + UUID.randomUUID();
        jdbcTemplate.update("INSERT into orders (id, status) VALUES (?,?)", savedOrderId, OrderStatus.CREATED.name());
        jdbcTemplate.update("INSERT into outbox_events (order_id) VALUES (?)", savedOrderId);
        smsProvider.stubFor(post(urlEqualTo("/sms")).willReturn(aResponse().withStatus(500)));
        LocalDateTime now = LocalDateTime.now().plusMinutes(3).withNano(0);
        assertDoesNotThrow(() -> outboxEventDeliveryWorker.deliverReadyEvents(now, 10));

        OutboxEvent event = jdbcTemplate
                .query("Select * from outbox_events where order_id =?", new OutboxEventRowMapper(), savedOrderId)
                .get(0);
        assertEquals(OutboxEventStatus.PENDING, event.status());
        assertEquals(1, event.attemptCount());
        assertEquals("SMS provider returned unexpected status: 500", event.lastError());
        assertNull(event.sentAt());
        assertEquals(now.plusMinutes(1), event.nextAttemptAt());

        smsProvider.verify(1, postRequestedFor(urlEqualTo("/sms"))
                .withHeader("Content-Type", equalTo("application/json"))
                .withHeader("Idempotency-Key", equalTo(String.valueOf(event.id())))
                .withRequestBody(matchingJsonPath("$.senderName", equalTo("OrderEngine")))
                .withRequestBody(matchingJsonPath("$.message", equalTo("Order is paid orderID:" + savedOrderId))));
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
        smsProvider.stubFor(post(urlEqualTo("/sms")).willReturn(aResponse().withStatus(202)));
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
        smsProvider.verify(1, postRequestedFor(urlEqualTo("/sms"))
                .withHeader("Content-Type", equalTo("application/json"))
                .withHeader("Idempotency-Key", equalTo(String.valueOf(passedEvent.id())))
                .withRequestBody(matchingJsonPath("$.senderName", equalTo("OrderEngine")))
                .withRequestBody(matchingJsonPath("$.message", equalTo("Order is paid orderID:" + savedOrderId))));
    }

    @Test
    public void doesNotSendDuplicateNotificationWhenRecoveredEventIsRetried() {
        savedOrderId = "postgres-outbox-delivery-test-" + UUID.randomUUID();
        jdbcTemplate.update("INSERT into orders (id, status) VALUES (?,?)", savedOrderId, OrderStatus.CREATED.name());
        jdbcTemplate.update("INSERT into outbox_events (order_id) VALUES (?)", savedOrderId);

        smsProvider.stubFor(post(urlEqualTo("/sms")).willReturn(aResponse().withStatus(202)));
        LocalDateTime firstClaimAt = LocalDateTime.now().withNano(0);
        LocalDateTime workerRunAt = firstClaimAt.plusMinutes(6);
        OutboxEvent firstClaim = outboxEventRepository.claimPendingReadyForDelivery(firstClaimAt.plusSeconds(3), 1)
                .get(0);

        assertEquals(OutboxEventStatus.PROCESSING, firstClaim.status());

        orderPaidNotificationService.notifyOrderPaid(new Order(savedOrderId), firstClaim.id());
        outboxEventDeliveryWorker.deliverReadyEvents(workerRunAt, 1);

        OutboxEvent passedEvent = jdbcTemplate
                .query("Select * from outbox_events where order_id =?", new OutboxEventRowMapper(), savedOrderId)
                .get(0);
        assertEquals(firstClaim.id(), passedEvent.id());
        assertEquals(OutboxEventStatus.SENT, passedEvent.status());
        assertEquals(workerRunAt, passedEvent.sentAt());
        assertEquals(0, passedEvent.attemptCount());
        smsProvider.verify(1, postRequestedFor(urlEqualTo("/sms"))
                .withHeader("Content-Type", equalTo("application/json"))
                .withHeader("Idempotency-Key", equalTo(String.valueOf(passedEvent.id())))
                .withRequestBody(matchingJsonPath("$.senderName", equalTo("OrderEngine")))
                .withRequestBody(matchingJsonPath("$.message", equalTo("Order is paid orderID:" + savedOrderId))));

    }
}
