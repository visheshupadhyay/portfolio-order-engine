package com.vishesh.notificationservice.notification;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.mockito.Mockito.mock;

class JdbcProcessedNotificationEventRepositoryTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

    private final JdbcProcessedNotificationEventRepository repository = new JdbcProcessedNotificationEventRepository(
            jdbcTemplate);

    @Test
    void returnsTrueWhenDatabaseInsertedTheEventId() {
        when(jdbcTemplate.update(
                """
                        INSERT INTO processed_notification_events (event_id)
                        VALUES (?)
                        ON CONFLICT (event_id) DO NOTHING
                        """,
                101L))
                .thenReturn(1);

        assertTrue(repository.markProcessedIfFirstTime(101L));
    }

    @Test
    void returnsFalseWhenDatabaseAlreadyHasTheEventId() {
        when(jdbcTemplate.update(
                """
                        INSERT INTO processed_notification_events (event_id)
                        VALUES (?)
                        ON CONFLICT (event_id) DO NOTHING
                        """,
                101L))
                .thenReturn(0);

        assertFalse(repository.markProcessedIfFirstTime(101L));
    }

    @Test
    void removesReservationWhenSendingWillNeedToBeRetried() {
        repository.removeProcessedEvent(101L);

        verify(jdbcTemplate).update(
                "DELETE FROM processed_notification_events WHERE event_id = ?",
                101L);
    }
}