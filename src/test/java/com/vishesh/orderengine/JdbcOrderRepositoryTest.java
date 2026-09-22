package com.vishesh.orderengine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

// Repository contract tests run against PostgreSQL rather than a mock, and each
// generated ID is deleted afterwards so tests do not share persistent state.
@ActiveProfiles("postgres")
@SpringBootTest
public class JdbcOrderRepositoryTest {
    @Autowired
    private JdbcTemplate jdbcTemplate;
    private String savedOrderId;

    @Test
    public void savesAndFindsAnOrderInPostgres() {
        JdbcOrderRepository repository = new JdbcOrderRepository(jdbcTemplate);
        savedOrderId = "jdbc-test-" + UUID.randomUUID();
        Order order = new Order(savedOrderId);
        repository.save(order);
        Order restored = repository.findOrderById(savedOrderId).orElseThrow();

        assertEquals(savedOrderId, restored.getId());
        assertEquals(OrderStatus.CREATED, restored.getStatus());
    }

    @AfterEach
    public void cleanUpDatabase() {
        if (savedOrderId != null) {
            jdbcTemplate.update("DELETE FROM orders WHERE id = ?", savedOrderId);
        }
    }

    @Test
    public void updatesTheStoredStatusWhenOrderIsPaid() {
        JdbcOrderRepository repository = new JdbcOrderRepository(jdbcTemplate);
        savedOrderId = "jdbc-test-" + UUID.randomUUID();
        Order order = new Order(savedOrderId);
        repository.save(order);
        order.markPaid();
        repository.save(order);
        Order restored = repository.findOrderById(savedOrderId).orElseThrow();
        assertEquals(OrderStatus.PAID, restored.getStatus());
    }

    @Test
    public void returnsEmptyWhenOrderDoesNotExist() {
        JdbcOrderRepository repository = new JdbcOrderRepository(jdbcTemplate);
        savedOrderId = "missing-jdbc-test-" + UUID.randomUUID();
        Optional<Order> received = repository.findOrderById(savedOrderId);
        assertTrue(received.isEmpty());
    }

    @Test
    public void findsSavedOrderInAllOrders() {
        JdbcOrderRepository repository = new JdbcOrderRepository(jdbcTemplate);
        savedOrderId = "jdbc-test-" + UUID.randomUUID();
        repository.save(new Order(savedOrderId));
        List<Order> orders = repository.findAll();

        assertTrue(orders.stream().anyMatch(order -> order.getId().equals(savedOrderId)));
    }

    @Test
    public void marksCreatedOrderPaidOnlyOnce() {
        JdbcOrderRepository repository = new JdbcOrderRepository(jdbcTemplate);
        savedOrderId = "jdbc-test-" + UUID.randomUUID();
        Order order = new Order(savedOrderId);
        repository.save(order);
        boolean result1 = repository.markPaidIfCreated(savedOrderId);
        assertTrue(result1);
        Order restored = repository.findOrderById(savedOrderId).orElseThrow();
        assertEquals(OrderStatus.PAID, restored.getStatus());
        boolean result2 = repository.markPaidIfCreated(savedOrderId);
        assertFalse(result2);
    }

    @Test
    public void createsOrderOnlyWhenIdIsNew() {
        JdbcOrderRepository repository = new JdbcOrderRepository(jdbcTemplate);
        savedOrderId = "jdbc-test-" + UUID.randomUUID();
        Order order1 = new Order(savedOrderId);
        Order order2 = new Order(savedOrderId);

        // Two Java objects simulate separate requests racing to create the same ID.
        assertTrue(repository.createIfAbsent(order1));
        assertFalse(repository.createIfAbsent(order2));
        Order storedOrder = repository.findOrderById(savedOrderId).orElseThrow();
        assertEquals(OrderStatus.CREATED, storedOrder.getStatus());
    }
}
