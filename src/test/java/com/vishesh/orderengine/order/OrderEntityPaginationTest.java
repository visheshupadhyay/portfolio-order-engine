package com.vishesh.orderengine.order;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

// Proves page metadata comes from the full matching result while content contains one sorted slice.
@SpringBootTest
@ActiveProfiles("postgres")
@Transactional
public class OrderEntityPaginationTest {
    @Autowired
    private OrderEntityJpaRepository orderEntityJpaRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    public void returnsSortedDatabasePage() {
        String uuid = "" + UUID.randomUUID();
        String savedOrderId1 = "jpa-pagination-a-" + uuid;
        String savedOrderId2 = "jpa-pagination-b-" + uuid;
        String savedOrderId3 = "jpa-pagination-c-" + uuid;
        OrderEntity order1 = new OrderEntity(savedOrderId1, OrderStatus.CREATED);
        OrderEntity order2 = new OrderEntity(savedOrderId2, OrderStatus.CREATED);
        OrderEntity order3 = new OrderEntity(savedOrderId3, OrderStatus.CREATED);
        orderEntityJpaRepository.saveAllAndFlush(List.of(order1, order2, order3));
        entityManager.clear();
        // Page numbering starts at 0; sorting uses the entity field name, not a SQL column name.
        Pageable firstPageRequest = PageRequest.of(0, 2, Sort.by("id").ascending());
        Page<OrderEntity> firstPage = orderEntityJpaRepository
                .findAllByIdIn(List.of(savedOrderId1, savedOrderId2, savedOrderId3), firstPageRequest);

        assertEquals(0, firstPage.getNumber());
        assertEquals(2, firstPage.getSize());
        assertEquals(3L, firstPage.getTotalElements());
        assertEquals(2, firstPage.getTotalPages());
        assertEquals(2, firstPage.getContent().size());
        assertEquals(savedOrderId1, firstPage.getContent().get(0).getId());
        assertEquals(savedOrderId2, firstPage.getContent().get(1).getId());

        Pageable secondPageRequest = PageRequest.of(1, 2, Sort.by("id").ascending());
        Page<OrderEntity> secondPage = orderEntityJpaRepository
                .findAllByIdIn(List.of(savedOrderId1, savedOrderId2, savedOrderId3), secondPageRequest);
        assertEquals(1, secondPage.getContent().size());
        assertEquals(savedOrderId3, secondPage.getContent().get(0).getId());
    }
}
