package com.vishesh.orderengine.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("postgres")
@Transactional
/* Verifies PostgreSQL cursor continuation and filtered cursor continuation without total counts. */
public class JdbcOrderRepositoryCursorTest {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    public void clearsDatabaseWithinTestTransaction() {
        // Each method starts with a deterministic table state; rollback restores previous rows.
        jdbcTemplate.update("DELETE FROM order_items");
        jdbcTemplate.update("DELETE FROM outbox_events");
        jdbcTemplate.update("DELETE FROM orders");
    }

    @Test
    public void returnsCursorBatchesFromPostgres() {
        JdbcOrderRepository jdbcOrderRepository = new JdbcOrderRepository(jdbcTemplate);
        Order orderA = new Order("cursor-a", OrderStatus.CREATED);
        Order orderB = new Order("cursor-b", OrderStatus.PAID);
        Order orderC = new Order("cursor-c", OrderStatus.CREATED);
        Order orderD = new Order("cursor-d", OrderStatus.PAID);
        jdbcOrderRepository.save(orderA);
        jdbcOrderRepository.save(orderB);
        jdbcOrderRepository.save(orderC);
        jdbcOrderRepository.save(orderD);
        // null after starts traversal; returned nextAfter becomes the next request's cursor.
        OrderCursorPage firstPage = jdbcOrderRepository.findAfter(null, null, 2);
        assertEquals(2, firstPage.content().size());
        assertEquals("cursor-a", firstPage.content().get(0).getId());
        assertEquals("cursor-b", firstPage.content().get(1).getId());
        assertEquals("cursor-b", firstPage.nextAfter());

        OrderCursorPage secondPage = jdbcOrderRepository.findAfter(null, "cursor-b", 2);
        assertEquals(2, secondPage.content().size());
        assertEquals("cursor-c", secondPage.content().get(0).getId());
        assertEquals("cursor-d", secondPage.content().get(1).getId());
        assertNull(secondPage.nextAfter());

        OrderCursorPage createdPage = jdbcOrderRepository.findAfter(OrderStatus.CREATED, null, 1);
        assertEquals(1, createdPage.content().size());
        assertEquals("cursor-a", createdPage.content().get(0).getId());
        assertEquals("cursor-a", createdPage.nextAfter());

        createdPage = jdbcOrderRepository.findAfter(OrderStatus.CREATED, "cursor-a", 1);
        assertEquals(1, createdPage.content().size());
        assertEquals("cursor-c", createdPage.content().get(0).getId());
        assertNull(createdPage.nextAfter());
    }

}
