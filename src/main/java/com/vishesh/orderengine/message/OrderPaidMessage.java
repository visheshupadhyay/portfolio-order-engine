package com.vishesh.orderengine.message;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The small, stable contract sent through Kafka when an order is paid.
 *
 * A producer can be deployed before every consumer has been upgraded. Unknown
 * future JSON fields are therefore ignored, so an added optional field does not
 * turn an otherwise usable event into a retry/DLT failure.
 */
@JsonIgnoreProperties (ignoreUnknown = true)
public record OrderPaidMessage(long eventId, String orderId) {
    
}
