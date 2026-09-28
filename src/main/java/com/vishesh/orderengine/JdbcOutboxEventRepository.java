package com.vishesh.orderengine;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

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
		// PostgreSQL supplies event type, PENDING state, counters, and timestamps via
		// defaults.
		jdbcTemplate.update("INSERT into outbox_events (order_id) VALUES (?)", orderId);
	}

	@Override
	public List<OutboxEvent> claimPendingReadyForDelivery(LocalDateTime now, int limit) {
		// This is one atomic database operation, not "SELECT then UPDATE":
		// candidates locks only available due rows; SKIP LOCKED lets another worker
		// immediately claim different rows; claimed changes only those selected rows
		// to PROCESSING and returns their new lease/token snapshot.
		// The ordering matches the (status, next_attempt_at, id) index and returns
		// oldest due work first.
		return jdbcTemplate.query(
				"""
							WITH candidates AS (
								SELECT id FROM outbox_events
								WHERE status = ? AND next_attempt_at <= ?
								ORDER BY next_attempt_at ASC, id ASC
								FOR UPDATE SKIP LOCKED LIMIT ?
							),
							claimed AS (
								UPDATE outbox_events
								SET status = ?, claimed_at = ?, claim_token = ?
								WHERE id IN (SELECT id FROM candidates) AND status = ?
								RETURNING *
							)
							SELECT * FROM claimed ORDER BY next_attempt_at ASC, id ASC;
						""",
				new OutboxEventRowMapper(),
				OutboxEventStatus.PENDING.name(),
				now,
				limit,
				OutboxEventStatus.PROCESSING.name(),
				now,
				UUID.randomUUID().toString(),
				OutboxEventStatus.PENDING.name());
	}

	@Override
	public void markSent(long eventId, String claimToken, LocalDateTime sentAt) {
		// All completion writes require PROCESSING plus the exact token. If a lease
		// expired and another worker reclaimed it, this UPDATE changes zero rows.
		jdbcTemplate.update(
				"UPDATE outbox_events SET claim_token=null, status = ?,sent_at = ?,last_error = null, claimed_at= null WHERE id = ? and status = ? and claim_token is not null and claim_token=?",
				OutboxEventStatus.SENT.name(), sentAt, eventId, OutboxEventStatus.PROCESSING.name(),claimToken);
	}

	@Override
	public void rescheduleAfterFailure(long eventId, String claimToken, String error, LocalDateTime nextAttemptAt) {
		// Increment in SQL so the persisted count remains correct across application
		// restarts.
		jdbcTemplate.update(
				"UPDATE outbox_events SET claim_token=null, attempt_count = attempt_count + 1 , status = ?,sent_at = ?,last_error = ?,next_attempt_at=?, claimed_at = null WHERE id = ? and status = ? and claim_token is not null and claim_token=?",
				OutboxEventStatus.PENDING.name(), null, error, nextAttemptAt, eventId,
				OutboxEventStatus.PROCESSING.name(),claimToken);
	}

	@Override
	public void markFailed(long eventId, String claimToken, String error) {
		// Terminal failure remains auditable but is excluded from future PENDING reads.
		jdbcTemplate.update(
				"UPDATE outbox_events SET claim_token=null,attempt_count = attempt_count + 1 , status = ?,sent_at = ?,last_error = ?,claimed_at=null WHERE id = ? and status = ? and claim_token is not null and claim_token=?",
				OutboxEventStatus.FAILED.name(), null, error, eventId, OutboxEventStatus.PROCESSING.name(), claimToken);
	}

	@Override
	public int releaseExpiredClaims(LocalDateTime claimedBefore) {
		// Clear the full lease (timestamp + ownership token), not merely the status.
		// This makes a later claim receive fresh ownership.
		int releasedEvents = jdbcTemplate.update(
				"UPDATE outbox_events SET claim_token=null,status = ?, claimed_at=null WHERE claimed_at is not null and claimed_at <= ? and status = ?",
				OutboxEventStatus.PENDING.name(), claimedBefore, OutboxEventStatus.PROCESSING.name());

		return releasedEvents;
	}

}
