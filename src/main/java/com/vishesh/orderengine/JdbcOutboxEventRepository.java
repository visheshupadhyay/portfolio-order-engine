package com.vishesh.orderengine;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Profile("postgres | jpa")
@Repository
public class JdbcOutboxEventRepository implements OutboxEventRepository {

    private JdbcTemplate jdbcTemplate;

    public JdbcOutboxEventRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void enqueueOrderPaid(String orderId) {
        // PostgreSQL supplies event type, PENDING state, counters, and timestamps via defaults.
        jdbcTemplate.update("INSERT into outbox_events (order_id) VALUES (?)", orderId);
    }

    @Override
    public List<OutboxEvent> findPendingReadyForDelivery(LocalDateTime now, int limit) {
        // Matches the (status, next_attempt_at, id) index and returns oldest due work first.
        return jdbcTemplate.query(
                "SELECT * FROM outbox_events WHERE next_attempt_at <= ? and status =? ORDER BY next_attempt_at,id ASC LIMIT ?",
                new OutboxEventRowMapper(),
                now,
                OutboxEventStatus.PENDING.name(), limit);

    }

    @Override
    public void markSent(long eventId, LocalDateTime sentAt) {
        // The status guard avoids changing an event already processed by another worker.
        jdbcTemplate.update(
                "UPDATE outbox_events SET status = ?,sent_at = ?,last_error = null WHERE id = ? and status = ?",
                OutboxEventStatus.SENT.name(), sentAt, eventId, OutboxEventStatus.PENDING.name());
    }

    @Override
    public void rescheduleAfterFailure(long eventId, String error, LocalDateTime nextAttemptAt) {
        // Increment in SQL so the persisted count remains correct across application restarts.
        jdbcTemplate.update(
                "UPDATE outbox_events SET attempt_count = attempt_count + 1 , status = ?,sent_at = ?,last_error = ?,next_attempt_at=? WHERE id = ? and status = ?",
                OutboxEventStatus.PENDING.name(), null, error,nextAttemptAt, eventId, OutboxEventStatus.PENDING.name());
    }

    @Override
    public void markFailed(long eventId, String error) {
        // Terminal failure remains auditable but is excluded from future PENDING reads.
        jdbcTemplate.update(
                "UPDATE outbox_events SET attempt_count = attempt_count + 1 , status = ?,sent_at = ?,last_error = ? WHERE id = ? and status = ?",
                OutboxEventStatus.FAILED.name(), null, error, eventId, OutboxEventStatus.PENDING.name());
    }

}
