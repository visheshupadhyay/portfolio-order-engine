package com.vishesh.notificationservice.message;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * JSON contract received from the Order Engine through Kafka.
 *
 * This class belongs to Notification Service. It intentionally does not import
 * any Order Engine Java code; the two applications communicate only through
 * this stable event shape.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderPaidMessage(long eventId, String orderId) {
    
}