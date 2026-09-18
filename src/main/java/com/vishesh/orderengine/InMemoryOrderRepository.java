package com.vishesh.orderengine;

/*
 * Spring-discovered repository implementation used for learning. It stores
 * orders in memory behind the OrderRepository contract and can later be
 * replaced by a database-backed implementation.
 */
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Repository;

@Repository 
public class InMemoryOrderRepository implements OrderRepository {
    private final Map<String, Order> map = new HashMap<>();

    @Override
    public void save(Order order) {
        map.put(order.getId(), order);
    }

    @Override
    public Optional<Order> findOrderById(String orderId) {
        Optional<Order> found = Optional.ofNullable(map.get(orderId));
        if (found.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(map.get(orderId));
    }
}
