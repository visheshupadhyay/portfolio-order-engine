package com.vishesh.orderengine.message;

/**
 * The boundary between the durable outbox worker and the message broker.
 *
 * Keeping this as an interface lets the worker describe its real job as
 * "publish an order-paid event", without knowing whether Kafka, a test fake,
 * or another transport performs that work.
 */
public interface OrderPaidEventPublisher {
    void publish(OrderPaidMessage message);
}
