package com.vishesh.orderengine;

import java.util.ArrayList;
import java.util.Comparator;
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

    @Override
    public OrderPage findPage(OrderStatus status, int page, int size) {
        // This emulates the database contract for tests: filter/sort first, then slice by offset.
        List<Order> matchingOrders = map.values()
                .stream()
                .filter(order -> status == null || order.getStatus() == status)
                .sorted(Comparator.comparing(Order::getId)).toList();
        int totalElements = matchingOrders.size();
        int startIndex = Math.min(page * size, totalElements);
        int endIndex = Math.min(startIndex + size, totalElements);
        List<Order> content = matchingOrders.subList(startIndex, endIndex);
        return new OrderPage(content, page, size, totalElements);
    }

    @Override
    public OrderCursorPage findAfter(OrderStatus status, String after, int size) {
        // Fetch one extra item to determine whether the response needs an outgoing cursor.
        List<Order> candidates = map.values()
                .stream()
                .filter(order -> status == null || order.getStatus() == status)
                .filter(order -> after == null || order.getId().compareTo(after) > 0)
                .sorted(Comparator.comparing(Order::getId)).limit(size + 1).toList();

        boolean hasMore = candidates.size() > size;
        int startIndex =0;
        int endIndex = Math.min(size,candidates.size());
        List<Order> content = candidates.subList(startIndex, endIndex);
        String nextAfter = hasMore ? content.getLast().getId() : null;

        return new OrderCursorPage(content, nextAfter);

    }
}
