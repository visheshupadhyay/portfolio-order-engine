package com.vishesh.orderengine.order;

import com.vishesh.orderengine.order.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

// Smallest real-database smoke test: it proves the active profile, driver, pool,
// credentials, and PostgreSQL connection can work together.
@ActiveProfiles ("postgres")
@SpringBootTest
class PostgresConnectionTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void connectsToPostgres() {
        Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);

        assertEquals(1, result);
    }
}
