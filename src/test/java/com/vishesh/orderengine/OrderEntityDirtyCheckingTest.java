package com.vishesh.orderengine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

// Contrasts managed entities (automatic update at flush) with detached objects (no update).
@SpringBootTest
@ActiveProfiles("postgres")
@Transactional
public class OrderEntityDirtyCheckingTest {
    @Autowired
    private OrderEntityJpaRepository orderEntityJpaRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    public void updatesManagedEntityThroughDirtyChecking() {
        String savedOrderId = "jpa-dirty-checking-test-" + UUID.randomUUID();
        OrderEntity orderEntity = new OrderEntity(savedOrderId, OrderStatus.CREATED);
        orderEntityJpaRepository.saveAndFlush(orderEntity);
        entityManager.clear();
        OrderEntity managedOrder = orderEntityJpaRepository.findById(savedOrderId).orElseThrow();
        assertTrue(entityManager.contains(managedOrder));
        // No repository save call: Hibernate notices this managed object's changed state.
        managedOrder.updateStatus(OrderStatus.PAID);
        entityManager.flush();
        entityManager.clear();
        OrderEntity reloadedOrder = orderEntityJpaRepository.findById(savedOrderId).orElseThrow();
        assertEquals(OrderStatus.PAID, reloadedOrder.getStatus());
    }

    @Test
    public void doesNotPersistChangesToDetachedEntity() {
        String savedOrderId = "jpa-detached-test-" + UUID.randomUUID();
        OrderEntity orderEntity = new OrderEntity(savedOrderId, OrderStatus.CREATED);
        orderEntityJpaRepository.saveAndFlush(orderEntity);
        entityManager.clear();
        OrderEntity detachedOrder = orderEntityJpaRepository.findById(savedOrderId).orElseThrow();
        entityManager.clear();
        assertFalse(entityManager.contains(detachedOrder));
        // Detached Java objects can change locally, but Hibernate no longer tracks them.
        detachedOrder.updateStatus(OrderStatus.PAID);
        entityManager.flush();
        entityManager.clear();
        OrderEntity finalOrder = orderEntityJpaRepository.findById(savedOrderId).orElseThrow();
        assertEquals(OrderStatus.CREATED, finalOrder.getStatus());
    }
}
