package com.vishesh.orderengine;

/*
 * Storage contract used by the service. Depending on this abstraction allows
 * Spring to supply the in-memory implementation today and a database
 * implementation later.
 */
import java.util.Optional;

interface OrderRepository {
    void save(Order order);
    Optional<Order> findOrderById(String orderId);
}
