package com.vishesh.orderengine.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

// Proves cascade persistence and a reloaded parent-to-child relationship.
@SpringBootTest
@ActiveProfiles("postgres")
@Transactional
public class OrderEntityItemsPersistenceTest {
    @Autowired
    private OrderEntityJpaRepository orderEntityJpaRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    public void cascadesOrderItemsAndReloadsTheirRelationship() {
        String savedOrderId = "jpa-order-items-test-" + UUID.randomUUID();
        OrderEntity orderEntity = new OrderEntity(savedOrderId, OrderStatus.CREATED);
        orderEntity.addItem("Keyboard", 2);
        orderEntity.addItem("Mouse", 1);
        /*
         * An assigned ID can make Spring Data JPA use merge: persist manages the same
         * instance, while merge returns a managed copy. Inspect savedOrder for generated child IDs.
         */
        OrderEntity savedOrder = orderEntityJpaRepository.saveAndFlush(orderEntity);
        List<OrderItemEntity> items = savedOrder.getItems();
        assertNotNull(items.get(0).getId());
        assertNotNull(items.get(1).getId());
        // Ensure the relationship is read from PostgreSQL, not the original Java list.
        entityManager.clear();
        OrderEntity reloadedOrder = orderEntityJpaRepository.findById(savedOrderId).orElseThrow();
        List<OrderItemEntity> returnedOrderItems = reloadedOrder.getItems();
        assertEquals(2, returnedOrderItems.size());
        assertTrue(returnedOrderItems.stream()
                .anyMatch(item -> item.getProductName().equals("Keyboard") && item.getQuantity() == 2));
        assertTrue(returnedOrderItems.stream()
                .anyMatch(item -> item.getProductName().equals("Mouse") && item.getQuantity() == 1));
    }
}
