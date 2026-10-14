package com.vishesh.notificationservice.integration;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;

@SpringBootTest
public class NotificationServiceIntegrationSmokeTest extends AbstractNotificationServiceIntegrationTest {
    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;


    @Test
    public void test() {
        assertNotNull(kafkaTemplate);
        assertTrue(kafka.isRunning());
        assertTrue(postgres.isRunning());
    }
}
