package com.vishesh.orderengine.order;

import com.vishesh.orderengine.integration.AbstractPostgresIntegrationTest;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

// Spring hides PostgreSQL driver details behind portable DataAccessException types.
@SpringBootTest
@ActiveProfiles("postgres")
public class JdbcExceptionTranslationTest  extends AbstractPostgresIntegrationTest {
    @Autowired
    private JdbcTemplate jdbcTemplate;
    private String savedOrderId;

    @AfterEach
    public void cleanUpDatabase() {
        if (savedOrderId != null) {

            jdbcTemplate.update("DELETE FROM outbox_events WHERE order_id = ?", savedOrderId);
            jdbcTemplate.update("DELETE FROM orders WHERE id = ?", savedOrderId);
        }
    }

    @Test
    public void translatesDuplicatePrimaryKeyIntoDataIntegrityViolation() {
        savedOrderId = "jdbc-exception-translation-test-" + UUID.randomUUID();
        jdbcTemplate.update("INSERT into orders (id, status) VALUES (?,?)", savedOrderId, OrderStatus.CREATED.name());
        // Deliberately bypass ON CONFLICT so PostgreSQL raises a duplicate-key error
        // which Spring should translate to DataIntegrityViolationException.
        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate
                .update("INSERT into orders (id, status) VALUES (?,?)", savedOrderId, OrderStatus.CREATED.name()));
    }
}
