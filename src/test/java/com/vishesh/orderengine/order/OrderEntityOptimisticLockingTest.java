package com.vishesh.orderengine.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

// Separate TransactionTemplate calls create detached copies that simulate two requests.
@SpringBootTest
@ActiveProfiles("postgres")
public class OrderEntityOptimisticLockingTest {
    @Autowired
    private OrderEntityJpaRepository orderEntityJpaRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private String savedOrderId;

    @AfterEach
    public void cleanUpDatabase() {
        if (savedOrderId != null) {
            transactionTemplate.executeWithoutResult(status -> orderEntityJpaRepository.deleteById(savedOrderId));
        }
    }

    @Test
    public void rejectsSecondSaveFromStaleVersion() {
        savedOrderId = "jpa-optimistic-lock-test-" + UUID.randomUUID();
        OrderEntity orderEntity = new OrderEntity(savedOrderId, OrderStatus.CREATED);
        transactionTemplate.executeWithoutResult(status -> orderEntityJpaRepository.saveAndFlush(orderEntity));

        // Both copies read version 0, then leave their individual persistence contexts.
        OrderEntity firstCopy = transactionTemplate
                .execute(status -> orderEntityJpaRepository.findById(savedOrderId).orElseThrow());
        OrderEntity secondCopy = transactionTemplate
                .execute(status -> orderEntityJpaRepository.findById(savedOrderId).orElseThrow());

        firstCopy.updateStatus(OrderStatus.PAID);

        transactionTemplate.executeWithoutResult(status -> {
            orderEntityJpaRepository.saveAndFlush(firstCopy);
        });

        secondCopy.updateStatus(OrderStatus.PAID);

        // The second save still carries version 0, so Hibernate rejects it as stale.
        assertThrows(ObjectOptimisticLockingFailureException.class,
                () -> transactionTemplate.executeWithoutResult(status -> {
                    orderEntityJpaRepository.saveAndFlush(secondCopy);
                }));

        OrderEntity finalCopy = transactionTemplate
                .execute(status -> orderEntityJpaRepository.findById(savedOrderId).orElseThrow());
        assertEquals(OrderStatus.PAID, finalCopy.getStatus());
        assertEquals(1L, finalCopy.getVersion());
    }
}
