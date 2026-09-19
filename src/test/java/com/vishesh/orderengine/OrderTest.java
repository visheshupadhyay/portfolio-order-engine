package com.vishesh.orderengine;

/*
 * Domain-model revision: an Order starts CREATED, may become PAID once, and
 * rejects invalid IDs. It is plain Java rather than a Spring-managed bean.
 */
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class OrderTest {
    @Test
    void createOrderTest() {
        Order order = new Order("order-101");
        assertEquals("order-101", order.getId());
        assertEquals(OrderStatus.CREATED, order.getStatus());
    }

    @Test
    void markOrderPaidTest() {
        Order order = new Order("order-101");
        order.markPaid();
        assertEquals(OrderStatus.PAID, order.getStatus());
    }

    @Test
    void twicePayOrder() {
        Order order = new Order("order-101");
        order.markPaid();
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> order.markPaid());

        assertEquals("Only CREATED orders can be PAID", exception.getMessage());
    }

    @Test
    void nullOrderIDTest() {
        
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,() -> new Order(null));;

        assertEquals("Order cannot be blank", exception.getMessage());
    }

    @Test
    void blankOrderIDTest() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> new Order(""));

        assertEquals("Order cannot be blank", exception.getMessage());
    }
}
