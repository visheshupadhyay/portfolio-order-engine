package com.vishesh.orderengine.cache;

import com.vishesh.orderengine.order.OrderStatus;

/**
 * Small JSON-safe snapshot stored in Redis. It intentionally contains only
 * public order state, not repository or behavior details from the domain object.
 */
public record CachedOrder(String id, OrderStatus status) {
}
