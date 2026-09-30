package com.vishesh.orderengine.integration;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Shared PostgreSQL Testcontainers configuration for Spring-backed database
 * integration tests. Individual tests retain their own Spring profile and
 * transaction settings while receiving this container's connection details.
 */
public abstract class AbstractPostgresIntegrationTest {

    @SuppressWarnings("resource")
    protected static final PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("order_engine_test")
            .withUsername("test_user")
            .withPassword("test_password");

    static {
        // A single container remains available for every subclass during this
        // Maven test JVM. This keeps Spring's cached datasource properties valid.
        postgres.start();
    }

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        // Testcontainers chooses a random free host port. These overrides are
        // applied before Spring builds its datasource, replacing local settings.
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
}
