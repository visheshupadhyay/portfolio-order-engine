package com.vishesh.orderengine.order;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import com.vishesh.orderengine.cache.OrderCache;

/**
 * Implements cache-aside reads: Redis is checked first, and persistent storage
 * is used only for a cache miss. PostgreSQL remains the source of truth.
 */
@Service
public class OrderLookupService {
    private final OrderRepository repository;
    private final OrderCache cache;
    private static final Logger logger = LoggerFactory.getLogger(OrderLookupService.class);

    public OrderLookupService(OrderRepository repository, OrderCache cache) {
        this.repository = repository;
        this.cache = cache;
    }

    public Optional<Order> findById(String orderId) {
        try {
            Optional<Order> redisOrder = cache.findById(orderId);
            if (redisOrder.isPresent()) {
                return redisOrder;
            }
        } catch (DataAccessException e) {
            // A cache outage must reduce speed, not make an otherwise valid order unreadable.
            logger.warn("Redis cache read failed for orderId={}; using repository", orderId);
        }

        Optional<Order> postgresOrder = repository.findOrderById(orderId);
        if (postgresOrder.isPresent()) {
            try {
                cache.put(postgresOrder.get());
            } catch (DataAccessException e) {
                // The database result is still correct even when Redis cannot keep a copy.
                logger.warn("Redis cache write failed for orderId={}; returning repository result", orderId);
            }

        }
        return postgresOrder;

    }

    public void evict(String orderId) {
        try {
            cache.evict(orderId);
        } catch (DataAccessException e) {
            // A later TTL expiry limits staleness if Redis is unavailable during invalidation.
            logger.warn("Redis cache eviction failed for orderId={}; stale cache may remain until TTL expiry", orderId);
        }
    }
}
