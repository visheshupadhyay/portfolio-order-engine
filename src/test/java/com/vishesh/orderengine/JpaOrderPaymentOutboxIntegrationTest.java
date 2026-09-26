package com.vishesh.orderengine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("jpa")
/* Proves the JPA order adapter and JDBC outbox adapter still share one transaction boundary. */
public class JpaOrderPaymentOutboxIntegrationTest {
    @Autowired
    private OrderPaymentService orderPaymentService;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private OutboxEventRepository outboxEventRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private TransactionTemplate transactionTemplate;
    private String savedOrderId;

    @AfterEach
    public void cleanUpDatabase() {
        if (savedOrderId != null) {
            jdbcTemplate.update("DELETE FROM outbox_events WHERE order_id = ?", savedOrderId);
            jdbcTemplate.update("DELETE FROM orders WHERE id = ?", savedOrderId);
        }
    }

    @Test
    public void commitsPaidJpaOrderAndPendingJdbcOutboxEvent() {
        // The profile deliberately combines a JPA order adapter with the reusable JDBC outbox adapter.
        assertInstanceOf(JpaOrderRepository.class, orderRepository);
        assertInstanceOf(JdbcOutboxEventRepository.class, outboxEventRepository);
        savedOrderId = "postgres-outbox-integration-test-" + UUID.randomUUID();
        Order order = new Order(savedOrderId, OrderStatus.CREATED);
        orderRepository.save(order);
        Order returnedOrder = orderPaymentService.pay(order);
        Order reloadedOrder = orderRepository.findOrderById(savedOrderId).orElseThrow();
        Long rows = jdbcTemplate.queryForObject("Select count(id) from outbox_events where order_id=?", Long.class, savedOrderId);
        assertEquals(OrderStatus.PAID, reloadedOrder.getStatus());
        assertEquals(OrderStatus.PAID, returnedOrder.getStatus());
        assertEquals(1L, rows);

    }

    @Test
    public void rollsBackJpaOrderAndJdbcOutboxEventWhenOuterTransactionFails() {
        savedOrderId = "postgres-outbox-integration-test-" + UUID.randomUUID();
        Order order = new Order(savedOrderId, OrderStatus.CREATED);
        orderRepository.save(order);
        assertThrows(IllegalStateException.class, () -> transactionTemplate.executeWithoutResult(status -> {
            orderPaymentService.pay(order);
            // The outer transaction must undo both the JPA update and JDBC insert.
            throw new IllegalStateException("simulate failure after payment");
        }));
        Order reloadedOrder = orderRepository.findOrderById(savedOrderId).orElseThrow();
        Long rows = jdbcTemplate.queryForObject("Select count(id) from outbox_events where order_id=? and status=?",
                Long.class, savedOrderId, OutboxEventStatus.PENDING.name());
        assertEquals(OrderStatus.CREATED, reloadedOrder.getStatus());
        assertEquals(0L, rows);
    }
}
