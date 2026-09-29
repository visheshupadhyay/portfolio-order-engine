package com.vishesh.orderengine.order;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.UUID;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.PersistenceUnit;
import org.springframework.transaction.annotation.Transactional;

// Statistics are enabled only for this test so normal application startup has no metrics overhead.
@SpringBootTest("spring.jpa.properties.hibernate.generate_statistics=true")
@ActiveProfiles("postgres")
@Transactional
public class OrderEntityNPlusOneTest {

    @Autowired
    private OrderEntityJpaRepository orderEntityJpaRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @PersistenceUnit
    private EntityManagerFactory entityManagerFactory;

    @Test
    public void createsOneLazyCollectionFetchPerOrder() {
        String savedOrderId1 = "jpa-n-plus-one-one-" + UUID.randomUUID();
        String savedOrderId2 = "jpa-n-plus-one-two-" + UUID.randomUUID();
        String savedOrderId3 = "jpa-n-plus-one-three-" + UUID.randomUUID();
        OrderEntity order1 = new OrderEntity(savedOrderId1, OrderStatus.CREATED);
        order1.addItem("Keyboard", 1);
        OrderEntity order2 = new OrderEntity(savedOrderId2, OrderStatus.CREATED);
        order2.addItem("Monitor", 2);
        OrderEntity order3 = new OrderEntity(savedOrderId3, OrderStatus.CREATED);
        order3.addItem("Mouse", 3);

        orderEntityJpaRepository.saveAllAndFlush(List.of(order1, order2, order3));
        entityManager.clear();

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        // Ignore setup SQL; measure only the list read and lazy item access below.
        statistics.clear();
        List<OrderEntity> totalOrders = orderEntityJpaRepository.findAllByIdIn(List.of(savedOrderId1,savedOrderId2,savedOrderId3));
        assertEquals(3, totalOrders.size());
        // Each first getItems call needs a separate lazy collection query: the N in N+1.
        for (OrderEntity order: totalOrders) {
            order.getItems().size();
        }
        assertEquals(3L, statistics.getCollectionFetchCount());
    }


}
