package com.vishesh.orderengine.order;

import com.vishesh.orderengine.integration.AbstractPostgresIntegrationTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("jpa")
@Transactional
/* JPA Slice-backed cursor contract: nextAfter exists only while another slice is available. */
public class JpaOrderRepositoryCursorIntegrationTest  extends AbstractPostgresIntegrationTest {
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    public void clearsDatabaseWithinTestTransaction() {
        // This rollback-only fixture must clear dependent rows before orders.
        jdbcTemplate.update("DELETE FROM order_items");
        jdbcTemplate.update("DELETE FROM outbox_events");
        jdbcTemplate.update("DELETE FROM orders");
    }

    @Test
    public void returnsCursorBatchesFromJpa() {
        // Confirm Spring selected the JPA adapter before validating its cursor behavior.
        assertInstanceOf(JpaOrderRepository.class, orderRepository);
        Order orderA = new Order("cursor-a", OrderStatus.CREATED);
        Order orderB = new Order("cursor-b", OrderStatus.PAID);
        Order orderC = new Order("cursor-c", OrderStatus.CREATED);
        Order orderD = new Order("cursor-d", OrderStatus.PAID);
        orderRepository.save(orderA);
        orderRepository.save(orderB);
        orderRepository.save(orderC);
        orderRepository.save(orderD);
        OrderCursorPage firstPage = orderRepository.findAfter(null, null, 2);
        assertEquals(2, firstPage.content().size());
        assertEquals("cursor-a", firstPage.content().get(0).getId());
        assertEquals("cursor-b", firstPage.content().get(1).getId());
        assertEquals("cursor-b", firstPage.nextAfter());

        OrderCursorPage secondPage = orderRepository.findAfter(null, "cursor-b", 2);
        assertEquals(2, secondPage.content().size());
        assertEquals("cursor-c", secondPage.content().get(0).getId());
        assertEquals("cursor-d", secondPage.content().get(1).getId());
        assertNull(secondPage.nextAfter());

        OrderCursorPage createdPage = orderRepository.findAfter(OrderStatus.CREATED, null, 1);
        assertEquals(1, createdPage.content().size());
        assertEquals("cursor-a", createdPage.content().get(0).getId());
        assertEquals("cursor-a", createdPage.nextAfter());

        createdPage = orderRepository.findAfter(OrderStatus.CREATED, "cursor-a", 1);
        assertEquals(1, createdPage.content().size());
        assertEquals("cursor-c", createdPage.content().get(0).getId());
        assertNull(createdPage.nextAfter());
    }

}
