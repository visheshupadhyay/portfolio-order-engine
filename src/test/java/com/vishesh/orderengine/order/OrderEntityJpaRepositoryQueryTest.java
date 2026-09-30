package com.vishesh.orderengine.order;

import com.vishesh.orderengine.integration.AbstractPostgresIntegrationTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

// Verifies that Spring Data derives a database filter from findAllByStatus(...).
@SpringBootTest
@ActiveProfiles("postgres")
@Transactional
public class OrderEntityJpaRepositoryQueryTest  extends AbstractPostgresIntegrationTest {
    @Autowired
    private OrderEntityJpaRepository orderEntityJpaRepository;

    @Test 
    public void findsOnlyOrdersWithRequestedStatus() {
        String createdOrderId = "jpa-query-created-" + UUID.randomUUID();
        String paidOrderId = "jpa-query-paid-" + UUID.randomUUID();

        OrderEntity orderEntity1 = new OrderEntity(createdOrderId, OrderStatus.CREATED);
        OrderEntity orderEntity2 = new OrderEntity(paidOrderId, OrderStatus.PAID);
        orderEntityJpaRepository.saveAllAndFlush(List.of(orderEntity1, orderEntity2));
        List<OrderEntity> paidOrders = orderEntityJpaRepository.findAllByStatus(OrderStatus.PAID);
        assertTrue(paidOrders.stream().anyMatch(order -> order.getId().equals(paidOrderId)));
        assertTrue(paidOrders.stream().noneMatch(order -> order.getId().equals(createdOrderId)));
    }
}
