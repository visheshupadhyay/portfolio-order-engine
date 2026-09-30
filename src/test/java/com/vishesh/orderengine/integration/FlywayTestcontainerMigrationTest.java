package com.vishesh.orderengine.integration;

import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
public class FlywayTestcontainerMigrationTest {
    // Unlike a test against an existing local database, this starts empty and
    // proves the migration files alone can create the schema from scratch.
    @SuppressWarnings("resource")
    @Container
    private static final PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("order_engine_test")
            .withUsername("test_user")
            .withPassword("test_password");

    @Test
    public void appliesAllMigrationsToFreshPostgres() throws Exception {
        Flyway flyway = Flyway.configure()
                .dataSource(
                        postgres.getJdbcUrl(),
                        postgres.getUsername(),
                        postgres.getPassword())
                .locations("classpath:db/migration")
                .load();

        try (Connection connection = DriverManager.getConnection(
                postgres.getJdbcUrl(),
                postgres.getUsername(),
                postgres.getPassword());
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(
                        "SELECT to_regclass('public.orders')")) {

            assertTrue(resultSet.next());
            assertNull(resultSet.getString(1));
        }

        // Flyway reads V1, V2, V3, and V4 in version order and records each
        // applied version in flyway_schema_history.
        flyway.migrate();

        try (Connection connection = DriverManager.getConnection(
                postgres.getJdbcUrl(),
                postgres.getUsername(),
                postgres.getPassword());
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(
                        "SELECT to_regclass('public.orders')")) {

            assertTrue(resultSet.next());
            assertNotNull(resultSet.getString(1));
        }
        try (Connection connection = DriverManager.getConnection(
                postgres.getJdbcUrl(),
                postgres.getUsername(),
                postgres.getPassword());
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("""
                        SELECT EXISTS (
                            SELECT 1
                            FROM information_schema.columns
                            WHERE table_schema = 'public'
                              AND table_name = 'outbox_events'
                              AND column_name = 'claim_token'
                        )
                        """)) {

            assertTrue(resultSet.next());
            assertTrue(resultSet.getBoolean(1));
        }
    }
}
