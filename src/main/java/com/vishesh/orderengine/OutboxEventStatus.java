package com.vishesh.orderengine;

// PENDING is deliverable when due; SENT and FAILED are terminal states for this worker.
enum OutboxEventStatus {
    PENDING,
    SENT,
    FAILED
}
