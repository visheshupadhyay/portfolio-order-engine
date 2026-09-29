package com.vishesh.orderengine.outbox;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;

import org.springframework.jdbc.core.RowMapper;

public class OutboxEventRowMapper implements RowMapper<OutboxEvent> {

    @Override
    public OutboxEvent mapRow(ResultSet rs, int rowNum) throws SQLException {
        Long id = rs.getLong("id");
        // A null token/timestamp means this row is not currently owned by a worker.
        String claimToken = rs.getString("claim_token");
        String orderId = rs.getString("order_id");
        String eventType = rs.getString("event_type");
        OutboxEventStatus status = OutboxEventStatus.valueOf(rs.getString("status"));
        int attemptCount = rs.getInt("attempt_count");
        LocalDateTime nextAttemptAt = rs.getTimestamp("next_attempt_at").toLocalDateTime();
        LocalDateTime createdAt = rs.getTimestamp("created_at").toLocalDateTime();
        // sent_at is nullable until a provider call has successfully returned.
        Timestamp timestamp_sent = rs.getTimestamp("sent_at");
        LocalDateTime sentAt = (timestamp_sent == null) ? null : timestamp_sent.toLocalDateTime();
        // PostgreSQL timestamps are nullable here because a completed, retried, or
        // released event must no longer retain an active lease.
        Timestamp timestamp_claim = rs.getTimestamp("claimed_at");
        LocalDateTime claimedAt = (timestamp_claim == null) ? null : timestamp_claim.toLocalDateTime();
        String lastError = rs.getString("last_error");
        return new OutboxEvent(id, claimToken, orderId, eventType, status, attemptCount, nextAttemptAt, claimedAt, createdAt,
                sentAt,
                lastError);
    }

}
