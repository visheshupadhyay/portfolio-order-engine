package com.vishesh.orderengine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

// Shows Hibernate initializes a new version at 0 and increments it on a managed update.
@SpringBootTest
@ActiveProfiles("postgres")
@Transactional
public class OrderEntityVersionTest {
    @Autowired
    private OrderEntityJpaRepository orderEntityJpaRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    public void startsAtZeroAndIncrementsAfterManagedUpdate() {
        String savedOrderId = "jpa-version-test-" + UUID.randomUUID();
        OrderEntity orderEntity = new OrderEntity(savedOrderId, OrderStatus.CREATED);
        OrderEntity savedOrder = orderEntityJpaRepository.saveAndFlush(orderEntity);
        assertEquals(0L, savedOrder.getVersion());
        entityManager.clear();
        OrderEntity reloadOrder = orderEntityJpaRepository.findById(savedOrderId).orElseThrow();
        assertEquals(0L,reloadOrder.getVersion());
        // Flush generates the version-checked UPDATE and moves the managed version to 1.
        reloadOrder.updateStatus(OrderStatus.PAID);
        entityManager.flush();
        assertEquals(1L,reloadOrder.getVersion());
        entityManager.clear();
        OrderEntity finalReloadOrder = orderEntityJpaRepository.findById(savedOrderId).orElseThrow();
        assertEquals(OrderStatus.PAID, finalReloadOrder.getStatus());
        assertEquals(1L,finalReloadOrder.getVersion());
    }
}
