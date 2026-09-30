package com.vishesh.orderengine.integration;

import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
public class PostgresTestcontainerSmokeTest {
    // This is the smallest proof that Testcontainers can start Docker's
    // PostgreSQL image and that Java can connect to it through JDBC.
    @SuppressWarnings("resource")
    @Container
    private static final PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("order_engine_test")
            .withUsername("test_user")
            .withPassword("test_password");

    @Test
    public void startsPostgreSqlContainer() {
        assertTrue(postgres.isRunning());
    }

    @Test
    public void connectsToContainerizedPostgresAndExecutesQuery() throws Exception {
        // SELECT 1 has no business meaning; it is the smallest database round
        // trip that proves the container, driver, credentials, and JDBC URL work.
        try (Connection connection = DriverManager.getConnection(
                postgres.getJdbcUrl(),
                postgres.getUsername(),
                postgres.getPassword());
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("SELECT 1")) {

            assertTrue(resultSet.next());
            assertEquals(1, resultSet.getInt(1));
        }

    }
}
