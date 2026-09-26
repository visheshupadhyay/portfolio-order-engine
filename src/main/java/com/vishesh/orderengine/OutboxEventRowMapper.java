package com.vishesh.orderengine;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;

import org.springframework.jdbc.core.RowMapper;

public class OutboxEventRowMapper implements RowMapper<OutboxEvent> {

    @Override
    public OutboxEvent mapRow(ResultSet rs, int rowNum) throws SQLException {
        Long id = rs.getLong("id");
        String orderId = rs.getString("order_id");
        String eventType = rs.getString("event_type");
        OutboxEventStatus status = OutboxEventStatus.valueOf(rs.getString("status"));
        int attemptCount = rs.getInt("attempt_count");
        LocalDateTime nextAttemptAt = rs.getTimestamp("next_attempt_at").toLocalDateTime();
        LocalDateTime createdAt = rs.getTimestamp("created_at").toLocalDateTime();
        // sent_at is nullable until a provider call has successfully returned.
        Timestamp timestamp = rs.getTimestamp("sent_at");
        LocalDateTime sentAt = (timestamp == null) ? null : timestamp.toLocalDateTime();
        String lastError = rs.getString("last_error");
        return new OutboxEvent(id, orderId, eventType, status, attemptCount, nextAttemptAt, createdAt, sentAt,
                lastError);
    }

}
