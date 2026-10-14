package com.vishesh.notificationservice.notification;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository  
public class JdbcProcessedNotificationEventRepository implements ProcessedNotificationEventRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcProcessedNotificationEventRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override 
    public boolean markProcessedIfFirstTime(long eventId) {
        int rowsInserted = jdbcTemplate.update(
                """
                INSERT INTO processed_notification_events (event_id)
                VALUES (?)
                ON CONFLICT (event_id) DO NOTHING
                """,
                eventId);

        return rowsInserted == 1;
    }

    @Override 
    public void removeProcessedEvent(long eventId) {
        jdbcTemplate.update("DELETE FROM processed_notification_events WHERE event_id = ?", eventId);
    } 
}
