package com.vishesh.orderengine.order;

import com.vishesh.orderengine.order.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import com.vishesh.orderengine.outbox.OutboxEventStatus;

@SpringBootTest
@ActiveProfiles("postgres")
/* Proves JDBC order changes and JDBC outbox rows commit/roll back as one PostgreSQL transaction. */
public class PostgresOrderPaymentOutboxIntegrationTest {
    @Autowired
    private OrderPaymentService orderPaymentService;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private TransactionTemplate transactionTemplate;
    private String saveOrderId;

    @AfterEach
    public void cleanUpDatabase() {
        if (saveOrderId != null) {
            jdbcTemplate.update("DELETE from outbox_events where order_id =?", saveOrderId);
            jdbcTemplate.update("DELETE from orders where id =?", saveOrderId);
        }
    }

    @Test
    public void commitsPaidOrderAndPendingOutboxEvent() {
        saveOrderId = "postgres-outbox-integration-test-" + UUID.randomUUID();
        Order order = new Order(saveOrderId, OrderStatus.CREATED);
        orderRepository.save(order);
        Order returnedOrder = orderPaymentService.pay(order);
        Order fetchedFromRepository = orderRepository.findOrderById(saveOrderId).orElseThrow();
        assertEquals(OrderStatus.PAID, returnedOrder.getStatus());
        assertEquals(OrderStatus.PAID, fetchedFromRepository.getStatus());
        // The same committed payment must leave exactly one durable delivery task.
        Long rows = jdbcTemplate.queryForObject("Select count(*) from outbox_events where order_id=? and status=?",
                Long.class, saveOrderId, OutboxEventStatus.PENDING.name());

        assertEquals(1L, rows);
    }

    @Test
    public void rollsBackPaidOrderAndOutboxEventWhenOuterTransactionFails() {
        saveOrderId = "postgres-outbox-integration-test-" + UUID.randomUUID();
        Order order = new Order(saveOrderId, OrderStatus.CREATED);
        orderRepository.save(order);
        assertThrows(IllegalStateException.class, () -> transactionTemplate.executeWithoutResult(status -> {
            orderPaymentService.pay(order);
            // Simulates later work failing after both writes have been attempted.
            throw new IllegalStateException("simulate failure after payment");
        }));
        Order reloadedOrder = orderRepository.findOrderById(saveOrderId).orElseThrow();
        assertEquals(OrderStatus.CREATED, reloadedOrder.getStatus());
        Long rows = jdbcTemplate.queryForObject("Select count(*) from outbox_events where order_id=?", Long.class,saveOrderId);
        assertEquals(0L, rows);

    }
}
