package com.vishesh.orderengine;

import java.time.LocalDateTime;
import java.util.List;

/*
 * Storage contract for the transactional-outbox lifecycle:
 * PENDING -> SENT on success, PENDING -> PENDING on delayed retry,
 * and PENDING -> FAILED after the worker reaches its retry limit.
 */
interface OutboxEventRepository {
    // Called inside the payment transaction; the inserted row must commit with PAID.
    void enqueueOrderPaid(String orderId);

    // Returns only work whose retry time has arrived, in deterministic oldest-first order.
    List<OutboxEvent> findPendingReadyForDelivery(LocalDateTime now, int limit);

    // A sent event is no longer eligible for the worker's PENDING query.
    void markSent(long eventId, LocalDateTime sentAt);

    // Retains failed work for a later attempt and records why this attempt failed.
    void rescheduleAfterFailure(long eventId, String error, LocalDateTime nextAttemptAt);

    // Stops retrying permanently while retaining an audit trail for investigation.
    void markFailed(long eventId, String error);
}
