package com.vishesh.orderengine.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
/* Verifies real LIMIT/OFFSET plus COUNT behavior; the transaction rolls back fixture rows. */
public class JdbcOrderRepositoryPaginationTest {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    public void clearsDatabaseWithinTestTransaction() {
        // Delete dependents before parents so foreign keys do not hide pagination behavior.
        jdbcTemplate.update("DELETE FROM order_items");
        jdbcTemplate.update("DELETE FROM outbox_events");
        jdbcTemplate.update("DELETE FROM orders");
    }

    @Test
    public void returnsSortedFilteredPagesFromPostgres() {
        JdbcOrderRepository jdbcOrderRepository = new JdbcOrderRepository(jdbcTemplate);
        String orderAId = "jdbc-page-a";
        String orderBId = "jdbc-page-b";
        String orderCId = "jdbc-page-c";

        Order orderA = new Order(orderAId, OrderStatus.CREATED);
        Order orderB = new Order(orderBId, OrderStatus.PAID);
        Order orderC = new Order(orderCId, OrderStatus.CREATED);
        jdbcOrderRepository.save(orderA);
        jdbcOrderRepository.save(orderB);
        jdbcOrderRepository.save(orderC);
        // Page zero and page one prove stable ID ordering and the final partial page.
        OrderPage firstPage = jdbcOrderRepository.findPage(null, 0, 2);
        assertEquals(0, firstPage.page());
        assertEquals(2, firstPage.size());
        assertEquals(3L, firstPage.totalElements());
        assertEquals(2, firstPage.content().size());
        assertTrue(firstPage.content().get(0).getId().equals("jdbc-page-a"));
        assertTrue(firstPage.content().get(1).getId().equals("jdbc-page-b"));
        OrderPage secondPage = jdbcOrderRepository.findPage(null, 1, 2);
        assertEquals(1, secondPage.content().size());
        assertTrue(secondPage.content().get(0).getId().equals("jdbc-page-c"));
        OrderPage createdOrdersPage = jdbcOrderRepository.findPage(OrderStatus.CREATED, 0, 2);
        assertEquals(2L, createdOrdersPage.totalElements());
        assertEquals(2, createdOrdersPage.content().size());
        assertTrue(createdOrdersPage.content().get(0).getId().equals("jdbc-page-a"));
        assertTrue(createdOrdersPage.content().get(1).getId().equals("jdbc-page-c"));
    }
}
