package com.vishesh.notificationservice.integration;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

public abstract class AbstractNotificationServiceIntegrationTest {
    @SuppressWarnings("resource")
    protected static final PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("notification_service_test")
            .withUsername("test_user")
            .withPassword("test_password");

    @SuppressWarnings("resource")
    protected static final KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("apache/kafka:4.3.1"));

    static {
        // A single container remains available for every subclass during this
        // Maven test JVM. This keeps Spring's cached datasource properties valid.
        postgres.start();
        kafka.start();
    }

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        // Testcontainers chooses a random free host port. These overrides are
        // applied before Spring builds its datasource, replacing local settings.
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.kafka.listener.auto-startup", () -> true);
        registry.add("notification.kafka.consumer-group", () -> "notification-service-integration-test");
    }
}
