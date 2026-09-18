package com.vishesh.orderengine;

/*
 * Plain-Java repository revision: saved orders are returned through Optional,
 * while an unknown ID returns Optional.empty().
 */

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.vishesh.orderengine.InMemoryOrderRepository;
import com.vishesh.orderengine.Order;

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

}
