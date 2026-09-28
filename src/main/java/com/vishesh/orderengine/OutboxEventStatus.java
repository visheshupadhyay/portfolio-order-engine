package com.vishesh.orderengine;

// PENDING is deliverable when due. PROCESSING is a temporary worker lease; SENT
// and FAILED are terminal states for this worker.
enum OutboxEventStatus {
    PENDING,
    PROCESSING,
    SENT,
    FAILED
}
