package com.vishesh.orderengine.cache;

import java.util.Optional;

import com.vishesh.orderengine.order.Order;

/**
 * Cache boundary for orders. Lookup policy belongs in OrderLookupService, while
 * implementations of this interface only know how to store or remove cache entries.
 */
public interface OrderCache {
    /** Returns an empty Optional for a normal cache miss. */
    Optional<Order> findById(String orderId);

    /** Stores a temporary snapshot; it does not change the source-of-truth database. */
    void put(Order order);

    /** Removes a potentially stale entry so a later read rebuilds it from storage. */
    void evict(String orderId);
}
