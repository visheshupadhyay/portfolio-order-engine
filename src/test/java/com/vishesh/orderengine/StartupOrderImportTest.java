package com.vishesh.orderengine;

/*
 * Boot startup integration: @SpringBootTest runs CommandLineRunner, which
 * imports configured orders before this test reads the repository.
 */

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class StartupOrderImportTest {

    @Autowired
    private OrderRepository orderRepository;

    @Test
    public void importsConfiguredOrdersWhenApplicationStarts() {
        Optional<Order> optionalOrder = orderRepository.findOrderById("order-101");

        assertTrue(optionalOrder.isPresent());

        Order order = optionalOrder.orElseThrow();
        assertEquals(OrderStatus.CREATED, order.getStatus());
    }
}
