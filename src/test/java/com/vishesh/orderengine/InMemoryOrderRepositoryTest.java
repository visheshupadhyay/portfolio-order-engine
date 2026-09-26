package com.vishesh.orderengine;

/*
 * Plain-Java repository revision: saved orders are returned through Optional,
 * while an unknown ID returns Optional.empty().
 */

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

public class InMemoryOrderRepositoryTest {
    @Test
    void saveOrderTest() {
        InMemoryOrderRepository orderRepository = new InMemoryOrderRepository();
        Order order = new Order("order-101");
        orderRepository.save(order);

        Optional<Order> found = orderRepository.findOrderById(order.getId());
        assertTrue(found.isPresent());
        assertEquals("order-101", found.orElseThrow().getId());
    }

    @Test
    void missingOrderReturnsEmpty() {
        InMemoryOrderRepository orderRepository = new InMemoryOrderRepository();
        Order order = new Order("order-101");
        orderRepository.save(order);
        Order wrongOrder = new Order("order-102");

        Optional<Order> found = orderRepository.findOrderById(wrongOrder.getId());
        assertFalse(found.isPresent());
    }

    @Test
    public void returnsSortedFilteredPages() {
        InMemoryOrderRepository inMemoryOrderRepository = new InMemoryOrderRepository();
        Order orderA = new Order("page-a", OrderStatus.CREATED);
        Order orderB = new Order("page-b", OrderStatus.PAID);
        Order orderC = new Order("page-c", OrderStatus.CREATED);
        inMemoryOrderRepository.save(orderA);
        inMemoryOrderRepository.save(orderB);
        inMemoryOrderRepository.save(orderC);
        // In-memory behavior must mirror the ID-sorted, filtered page contract used by JDBC/JPA.
        OrderPage firstPage = inMemoryOrderRepository.findPage(null, 0, 2);
        assertEquals(2, firstPage.size());
        assertEquals(3L, firstPage.totalElements());
        assertEquals(2, firstPage.content().size());
        assertEquals(0, firstPage.page());
        assertTrue(firstPage.content().get(0).getId().equals("page-a"));
        assertTrue(firstPage.content().get(1).getId().equals("page-b"));
        OrderPage secondPage = inMemoryOrderRepository.findPage(null, 1, 2);
        assertEquals(1, secondPage.content().size());
        assertTrue(secondPage.content().get(0).getId().equals("page-c"));
        OrderPage createdOrdersPage = inMemoryOrderRepository.findPage(OrderStatus.CREATED, 0, 2);
        assertEquals(2L, createdOrdersPage.totalElements());
        assertEquals(2, createdOrdersPage.content().size());
        assertTrue(createdOrdersPage.content().get(0).getId().equals("page-a"));
        assertTrue(createdOrdersPage.content().get(1).getId().equals("page-c"));
    }

    @Test
    public void returnsCursorBatchesAfterTheProvidedId() {
        InMemoryOrderRepository inMemoryOrderRepository = new InMemoryOrderRepository();
        Order orderA = new Order("cursor-a", OrderStatus.CREATED);
        Order orderB = new Order("cursor-b", OrderStatus.PAID);
        Order orderC = new Order("cursor-c", OrderStatus.CREATED);
        Order orderD = new Order("cursor-d", OrderStatus.PAID);
        inMemoryOrderRepository.save(orderA);
        inMemoryOrderRepository.save(orderB);
        inMemoryOrderRepository.save(orderC);
        inMemoryOrderRepository.save(orderD);
        // A null cursor starts traversal; the returned cursor continues from the last item.
        OrderCursorPage firstPage = inMemoryOrderRepository.findAfter(null, null, 2);
        assertEquals(2, firstPage.content().size());
        assertEquals("cursor-a", firstPage.content().get(0).getId());
        assertEquals("cursor-b", firstPage.content().get(1).getId());
        assertEquals("cursor-b", firstPage.nextAfter());

        OrderCursorPage secondPage = inMemoryOrderRepository.findAfter(null, "cursor-b", 2);
        assertEquals(2, secondPage.content().size());
        assertEquals("cursor-c", secondPage.content().get(0).getId());
        assertEquals("cursor-d", secondPage.content().get(1).getId());
        assertNull(secondPage.nextAfter());

        OrderCursorPage createdPage = inMemoryOrderRepository.findAfter(OrderStatus.CREATED, null, 1);
        assertEquals(1, createdPage.content().size());
        assertEquals("cursor-a", createdPage.content().get(0).getId());
        assertEquals("cursor-a", createdPage.nextAfter());

        createdPage = inMemoryOrderRepository.findAfter(OrderStatus.CREATED, "cursor-a", 1);
        assertEquals(1, createdPage.content().size());
        assertEquals("cursor-c", createdPage.content().get(0).getId());
        assertNull(createdPage.nextAfter());

    }
}
