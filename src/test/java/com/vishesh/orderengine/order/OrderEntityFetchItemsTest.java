package com.vishesh.orderengine.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.transaction.annotation.Transactional;

// The repository's targeted EntityGraph initializes items before detachment.
@SpringBootTest
@ActiveProfiles("postgres")
@Transactional
public class OrderEntityFetchItemsTest {
    @Autowired
    private OrderEntityJpaRepository orderEntityJpaRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    public void loadsRequestedItemsBeforeOrderIsDetached() {
        String savedOrderId = "jpa-fetch-items-test-" + UUID.randomUUID();
        OrderEntity orderEntity = new OrderEntity(savedOrderId, OrderStatus.CREATED);
        orderEntity.addItem("Keyboard", 2);
        orderEntity.addItem("Mouse", 1);
        orderEntityJpaRepository.saveAndFlush(orderEntity);
        entityManager.clear();
        OrderEntity orderWithItems = orderEntityJpaRepository.findWithItemsById(savedOrderId).orElseThrow();
        // Safe after clear because this particular query requested the child collection.
        entityManager.clear();
        assertFalse(entityManager.contains(orderWithItems));
        List<OrderItemEntity> items = orderWithItems.getItems();
        assertEquals(2, items.size());
        assertTrue(items.stream()
                .anyMatch(item -> item.getProductName().equals("Keyboard") && item.getQuantity() == 2));
        assertTrue(
                items.stream().anyMatch(item -> item.getProductName().equals("Mouse") && item.getQuantity() == 1));

    }
}
