package com.vishesh.orderengine.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

// Direct EntityManager baseline: persist -> flush to SQL -> clear cache -> find from DB.
@SpringBootTest
@ActiveProfiles("postgres")
@Transactional 
public class OrderEntityPersistenceTest {
    @PersistenceContext
    private EntityManager entityManager;

    @Test
    public void persistsAndReloadsOrderEntity() {
        String savedOrderId = "jpa-entity-test-" + UUID.randomUUID();
        OrderEntity orderEntity = new OrderEntity(savedOrderId, OrderStatus.CREATED);
        entityManager.persist(orderEntity);
        entityManager.flush();
        entityManager.clear();
        OrderEntity reloaded = entityManager.find(OrderEntity.class, savedOrderId);
        assertNotNull(reloaded);
        assertEquals(savedOrderId,reloaded.getId());
        assertEquals(OrderStatus.CREATED,reloaded.getStatus());
    }
}
