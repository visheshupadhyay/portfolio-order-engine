package com.vishesh.orderengine.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.vishesh.orderengine.integration.AbstractPostgresIntegrationTest;

@SpringBootTest
@ActiveProfiles("postgres")
/*
 * Verifies that PostgreSQL's primary key and ON CONFLICT create one durable
 * idempotency decision, even beyond the lifetime of an in-memory Java object.
 */
public class JdbcProcessedNotificationEventRepositoryTest extends AbstractPostgresIntegrationTest {
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private ProcessedNotificationEventRepository repository;
    private Long savedEventId;

    @AfterEach
    public void cleanUpDatabase() {
        if (savedEventId!=null) {
            jdbcTemplate.update("DELETE FROM processed_notification_events where event_id=?", savedEventId);
        }
    }

    @Test
    public void recordsAnEventOnlyOnceAndAllowsItAgainAfterRemoval() {
        long eventId = UUID.randomUUID().getMostSignificantBits();
        assertTrue(repository.markProcessedIfFirstTime(eventId));
        assertFalse(repository.markProcessedIfFirstTime(eventId));
        long events = jdbcTemplate.queryForObject(
                "Select Count(*) from processed_notification_events where event_id=?",
                Long.class, eventId);
        assertEquals(1, events);

        repository.removeProcessedEvent(eventId);
        assertTrue(repository.markProcessedIfFirstTime(eventId));
    }
}
