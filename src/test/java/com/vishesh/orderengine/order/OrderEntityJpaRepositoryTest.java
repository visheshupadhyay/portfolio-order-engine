package com.vishesh.orderengine.order;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

// Proves Spring Data supplies the ordinary save/find operations without handwritten SQL.
@SpringBootTest
@ActiveProfiles("postgres")
@Transactional
public class OrderEntityJpaRepositoryTest {
    @Autowired
    private OrderEntityJpaRepository orderEntityJpaRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    public void savesAndFindsOrderEntityWithSpringDataJpa() {
        String savedOrderId = "spring-jpa-test-" + UUID.randomUUID();
        OrderEntity orderEntity = new OrderEntity(savedOrderId, OrderStatus.CREATED);
        orderEntityJpaRepository.saveAndFlush(orderEntity);
        entityManager.clear();
        OrderEntity returnedOrder = orderEntityJpaRepository.findById(savedOrderId).orElseThrow();
        assertEquals(savedOrderId, returnedOrder.getId());
        assertEquals(OrderStatus.CREATED, returnedOrder.getStatus());
    }
}
