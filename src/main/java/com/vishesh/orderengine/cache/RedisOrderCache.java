package com.vishesh.orderengine.cache;

import java.time.Duration;
import java.util.Optional;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import com.vishesh.orderengine.order.Order;

/** Redis implementation of the cache boundary, using one JSON value per order key. */
@Component
public class RedisOrderCache implements OrderCache {

    private static final String KEY_PREFIX = "order:";

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration cacheTtl;

    public RedisOrderCache(StringRedisTemplate stringRedisTemplate, ObjectMapper objectMapper,
            @Value("${order.cache.ttl}") Duration cacheTtl) {

        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.cacheTtl = cacheTtl;
    }

    @Override
    public Optional<Order> findById(String orderId) {
        // Redis GET: a missing key is a normal cache miss, not an error.
        String cachedJson = stringRedisTemplate.opsForValue().get(cacheKey(orderId));
        if (cachedJson == null) {
            return Optional.empty();
        }

        try {
            // Redis stores JSON text, so rebuild the domain Order before returning it.
            CachedOrder cachedOrder = objectMapper.readValue(cachedJson, CachedOrder.class);
            return Optional.of(new Order(cachedOrder.id(), cachedOrder.status()));

        } catch (JacksonException exception) {
            throw new IllegalStateException("Could not read cached order", exception);
        }
    }

    @Override
    public void put(Order order) {
        try {
            // Store a data-only snapshot of the current source-of-truth Order with a fixed TTL.
            CachedOrder cachedOrder = new CachedOrder(order.getId(), order.getStatus());
            String jsonText = objectMapper.writeValueAsString(cachedOrder);
            stringRedisTemplate.opsForValue().set(cacheKey(order.getId()), jsonText, cacheTtl);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Could not write cached order", exception);
        }
    }

    @Override
    public void evict(String orderId) {
        // A database write may make this copy stale; remove it so the next read rebuilds it.
        stringRedisTemplate.delete(cacheKey(orderId));
    }

    private String cacheKey(String orderId) {
        // One consistent key format prevents lookup and eviction from targeting different entries.
        return KEY_PREFIX + orderId;
    }

}
