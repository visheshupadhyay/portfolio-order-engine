package com.vishesh.orderengine.order;

/**
 * A small in-process event: it says the payment transaction intends to report a
 * completed payment. It is not the Kafka event; the transactional outbox remains
 * the durable cross-process hand-off.
 */
public record OrderPaid(String orderId) {

}
