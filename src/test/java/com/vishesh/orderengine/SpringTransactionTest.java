package com.vishesh.orderengine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;


import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

// An observable rollback experiment against PostgreSQL: an exception after an
// insert must leave no committed row.
@SpringBootTest
@ActiveProfiles("postgres")
public class SpringTransactionTest {
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String savedOrderId;

    @Test
    public void rollsBackInsertedOrderWhenWorkFails() {
        savedOrderId = "spring-transaction-test-" + UUID.randomUUID();

        assertThrows(IllegalStateException.class, () -> {
            transactionTemplate.executeWithoutResult(transactionStatus -> {
                jdbcTemplate.update("INSERT into orders (id, status) VALUES (?,?)", savedOrderId,
                        OrderStatus.CREATED.name());
                // The exception must escape the callback so Spring marks the work for rollback.
                throw new IllegalStateException("Simulated failure");
            });
        });
        int rowCount = jdbcTemplate.queryForObject("Select count(id) from orders where id =?", Integer.class,
                savedOrderId);
        assertEquals(0, rowCount);
    }

    @AfterEach
    public void cleanUpDatabase() {
        if (savedOrderId != null) {
            jdbcTemplate.update("DELETE FROM orders WHERE id = ?", savedOrderId);
        }
    }
}
