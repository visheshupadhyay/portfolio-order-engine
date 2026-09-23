package com.vishesh.orderengine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

/*
 * Lightweight storage used only when neither database-backed profile is active.
 * It keeps the same OrderRepository contract, so controllers/services do not
 * know which storage implementation Spring selected.
 */
// The three persistence choices are mutually exclusive: default memory, JDBC, or JPA.
@Profile("!postgres & !jpa")
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

    @Override
    public List<Order> findAll() {
        // Return a snapshot so callers cannot mutate the repository's map through the
        // list.
        return new ArrayList<>(this.map.values());
    }

    @Override
    public synchronized boolean markPaidIfCreated(String orderId) {
        // One synchronized check-and-change prevents two in-memory callers from both
        // believing they made the CREATED -> PAID transition.
        if (map.containsKey(orderId) && map.get(orderId).getStatus() == OrderStatus.CREATED) {
            map.get(orderId).markPaid();
            return true;
        } else {
            return false;
        }
    }

    @Override
    public synchronized boolean createIfAbsent(Order order) {
        // null means no prior value existed, so this call inserted the new order.
        return map.putIfAbsent(order.getId(), order) == null;
    }
}
