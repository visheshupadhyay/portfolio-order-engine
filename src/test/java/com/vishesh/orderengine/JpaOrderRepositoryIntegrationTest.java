package com.vishesh.orderengine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

// End-to-end repository-contract test: profile selection, JPA adapter mapping,
// atomic PostgreSQL writes, and reads all run in one rollback-only test transaction.
@SpringBootTest
@ActiveProfiles("jpa")
@Transactional
public class JpaOrderRepositoryIntegrationTest {
    @Autowired
    private OrderRepository orderRepository;

    @Test
    public void usesJpaAdapterForAtomicOrderOperations() {
        assertInstanceOf(JpaOrderRepository.class,orderRepository);
        String savedOrderId = "jpa-order-repository-integration-test-" + UUID.randomUUID();
        boolean orderCreated = orderRepository.createIfAbsent(new Order(savedOrderId));
        assertTrue(orderCreated); // First insert wins the atomic create decision.
        orderCreated = orderRepository.createIfAbsent(new Order(savedOrderId));
        assertFalse(orderCreated); // Same ID is a duplicate; existing status remains CREATED.
        assertEquals(savedOrderId, orderRepository.findOrderById(savedOrderId).get().getId());
        assertEquals(OrderStatus.CREATED, orderRepository.findOrderById(savedOrderId).get().getStatus());

        boolean orderPaid = orderRepository.markPaidIfCreated(savedOrderId);
        assertTrue(orderPaid); // This request made the conditional CREATED -> PAID change.
        orderPaid = orderRepository.markPaidIfCreated(savedOrderId);
        assertFalse(orderPaid); // Retry observes PAID and cannot win the transition again.
        assertEquals(OrderStatus.PAID, orderRepository.findOrderById(savedOrderId).get().getStatus());
        orderRepository.save(new Order(savedOrderId, OrderStatus.CREATED)); // Verify UPSERT update path.
        assertEquals(OrderStatus.CREATED, orderRepository.findOrderById(savedOrderId).get().getStatus());
        assertTrue(orderRepository.findAll().stream().anyMatch(order-> order.getId().equals(savedOrderId)));
    }
}
