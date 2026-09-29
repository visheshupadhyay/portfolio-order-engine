package com.vishesh.orderengine.outbox;

import java.time.LocalDateTime;
import java.util.List;

/*
 * Storage contract for the transactional-outbox lifecycle:
 * PENDING -> PROCESSING when one worker claims an event; PROCESSING then moves
 * to SENT, PENDING (retry), or FAILED. An abandoned PROCESSING claim can return
 * to PENDING after its lease expires.
 */
public interface OutboxEventRepository {
    // Called inside the payment transaction; the inserted row must commit with PAID.
    void enqueueOrderPaid(String orderId);

    // Atomically claims due PENDING work. Returned records carry the temporary
    // claimToken that the same worker must present to finish or reschedule them.
    List<OutboxEvent> claimPendingReadyForDelivery(LocalDateTime now, int limit);

    // The id + token pair prevents an old worker from changing an event reclaimed
    // by a newer worker. A sent event is no longer eligible for the PENDING query.
    void markSent(long eventId,  String claimToken,LocalDateTime sentAt);

    // Retains failed work for a later attempt and records why this attempt failed.
    void rescheduleAfterFailure(long eventId, String claimToken, String error, LocalDateTime nextAttemptAt);

    // Stops retrying permanently while retaining an audit trail for investigation.
    void markFailed(long eventId, String claimToken, String error);

    // Recovers only leases claimed at or before the cutoff; it does not count as
    // a provider failure, so it does not increment attemptCount.
    int releaseExpiredClaims(LocalDateTime claimedBefore);
}
