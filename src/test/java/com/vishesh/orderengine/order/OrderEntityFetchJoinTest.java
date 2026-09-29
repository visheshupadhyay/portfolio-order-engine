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
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.PersistenceUnit;

// Companion to the N+1 test: fetch join initializes items in the original list query.
@SpringBootTest("spring.jpa.properties.hibernate.generate_statistics=true")
@ActiveProfiles("postgres")
@Transactional
public class OrderEntityFetchJoinTest {
    @Autowired
    private OrderEntityJpaRepository orderEntityJpaRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @PersistenceUnit
    private EntityManagerFactory entityManagerFactory;

    @Test
    public void loadsOrderItemsWithoutExtraLazyCollectionFetches() {
        String savedOrderId1 = "jpa-fetch-join-one-" + UUID.randomUUID();
        String savedOrderId2 = "jpa-fetch-join-two-" + UUID.randomUUID();
        String savedOrderId3 = "jpa-fetch-join-three-" + UUID.randomUUID();
        OrderEntity order1 = new OrderEntity(savedOrderId1, OrderStatus.CREATED);
        order1.addItem("Keyboard", 1);
        order1.addItem("USB", 1);
        OrderEntity order2 = new OrderEntity(savedOrderId2, OrderStatus.CREATED);
        order2.addItem("Monitor", 2);
        order2.addItem("Speakers", 2);
        OrderEntity order3 = new OrderEntity(savedOrderId3, OrderStatus.CREATED);
        order3.addItem("Mouse", 3);
        order3.addItem("Printer", 3);

        orderEntityJpaRepository.saveAllAndFlush(List.of(order1, order2, order3));
        entityManager.clear();

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        // Ignore setup SQL before measuring the fetch-join behavior.
        statistics.clear();

        List<OrderEntity> returnedOrders = orderEntityJpaRepository.findAllWithItemsByIdIn(List.of(savedOrderId1,savedOrderId2,savedOrderId3));
        assertEquals(3, returnedOrders.size());
        // Item access must not trigger any later lazy collection fetch.
        for (OrderEntity order : returnedOrders) {
            order.getItems().size();
        }
        assertEquals(0L, statistics.getCollectionFetchCount());
    }
}
