package com.vishesh.orderengine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

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
/* Same page contract as JDBC, but through the profile-selected JPA adapter and Spring Data Page. */
public class JpaOrderRepositoryPaginationIntegrationTest {
    @Autowired 
    private OrderRepository orderRepository;
    @Autowired 
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    public void clearsDatabaseWithinTestTransaction() {
        // Keep the PostgreSQL fixture isolated and obey child-before-parent foreign keys.
        jdbcTemplate.update("DELETE FROM order_items");
        jdbcTemplate.update("DELETE FROM outbox_events");
        jdbcTemplate.update("DELETE FROM orders");
    }

    @Test
    public void returnsSortedFilteredPagesThroughJpaAdapter() {
        // Confirm this test is exercising JPA rather than accidentally using the default repository.
        assertInstanceOf(JpaOrderRepository.class, orderRepository);
        String orderAId = "jpa-page-a";
        String orderBId = "jpa-page-b";
        String orderCId = "jpa-page-c";
        Order orderA = new Order(orderAId, OrderStatus.CREATED);
        Order orderB = new Order(orderBId, OrderStatus.PAID);
        Order orderC = new Order(orderCId, OrderStatus.CREATED);
        orderRepository.save(orderA);
        orderRepository.save(orderB);
        orderRepository.save(orderC);
        OrderPage firstPage = orderRepository.findPage(null, 0, 2);
        assertEquals(0, firstPage.page());
        assertEquals(2, firstPage.content().size());
        assertEquals(2, firstPage.size());
        assertEquals(3L, firstPage.totalElements());
        assertEquals("jpa-page-a", firstPage.content().get(0).getId());
        assertEquals("jpa-page-b", firstPage.content().get(1).getId());
        OrderPage secondPage = orderRepository.findPage(null, 1, 2);
        assertEquals(1, secondPage.page());
        assertEquals(1, secondPage.content().size());
        assertEquals(2, secondPage.size());
        assertEquals(3L, secondPage.totalElements());
        assertEquals("jpa-page-c", secondPage.content().get(0).getId());
        OrderPage createdOrderPage = orderRepository.findPage(OrderStatus.CREATED, 0, 2);

        assertEquals(2, createdOrderPage.content().size());
        assertEquals(2L, createdOrderPage.totalElements());
        assertEquals("jpa-page-a", createdOrderPage.content().get(0).getId());
        assertEquals("jpa-page-c", createdOrderPage.content().get(1).getId());

    }
}
