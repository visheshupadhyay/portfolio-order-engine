package com.vishesh.orderengine.notification;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component 
@Profile ("postgres | jpa")
public class JdbcProcessedNotificationEventRepository implements  ProcessedNotificationEventRepository{
    private final JdbcTemplate jdbcTemplate;

    public JdbcProcessedNotificationEventRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }
    @Override
    public boolean markProcessedIfFirstTime(long eventId) {
        // PostgreSQL performs this as one atomic operation. Two consumer attempts
        // cannot both insert the same primary key, so only one can send the SMS.
        int rows = jdbcTemplate.update("INSERT into processed_notification_events (event_id) VALUES (?) ON CONFLICT (event_id) DO NOTHING", eventId);

        return rows==1;
    }

    @Override
    public void removeProcessedEvent(long eventId) {
        // If sending fails, erase the reservation. A later Kafka retry must be
        // allowed to try the notification again instead of being skipped forever.
        jdbcTemplate.update("DELETE from processed_notification_events where event_id=?", eventId);
    }
    
}
