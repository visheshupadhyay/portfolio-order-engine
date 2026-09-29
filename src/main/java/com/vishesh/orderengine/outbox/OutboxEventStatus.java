package com.vishesh.orderengine.outbox;

// PENDING is deliverable when due. PROCESSING is a temporary worker lease; SENT
// and FAILED are terminal states for this worker.
public enum OutboxEventStatus {
    PENDING,
    PROCESSING,
    SENT,
    FAILED
}
