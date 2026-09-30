package com.vishesh.orderengine.order;

import com.vishesh.orderengine.integration.AbstractPostgresIntegrationTest;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import org.hibernate.LazyInitializationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

// A lazy collection needs an active persistence context for its first database load.
@SpringBootTest
@ActiveProfiles("postgres")
@Transactional
public class OrderEntityLazyLoadingTest  extends AbstractPostgresIntegrationTest {
    @Autowired
    private OrderEntityJpaRepository orderEntityJpaRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    public void throwsWhenDetachedOrderTriesToLoadLazyItems() {
        String savedOrderId = "jpa-lazy-loading-test-" + UUID.randomUUID();
        OrderEntity orderEntity = new OrderEntity(savedOrderId,OrderStatus.CREATED);
        orderEntity.addItem("Mouse", 3);
        orderEntityJpaRepository.saveAndFlush(orderEntity);
        entityManager.clear();
        OrderEntity detachedOrder = orderEntityJpaRepository.findById(savedOrderId).orElseThrow();
        // Clearing detaches the order before its lazy items collection has initialized.
        entityManager.clear();
        assertThrows(LazyInitializationException.class, ()-> detachedOrder.getItems().size());

    }
}
