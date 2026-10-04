package com.vishesh.orderengine.order;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.junit.jupiter.api.Test;

import com.vishesh.orderengine.cache.OrderCache;

/**
 * Fast policy tests: mocks let us force cache hits, misses, and failures.
 * Redis serialization and networking are covered separately by the Testcontainers test.
 */
public class OrderLookupServiceTest {

    private OrderRepository postgresRepository = mock(OrderRepository.class);

    private OrderCache rediscache = mock(OrderCache.class);

    @Test
    public void returnsCachedOrderWithoutCallingRepository() {
        OrderLookupService service = new OrderLookupService(postgresRepository, rediscache);
        Order order = new Order("order-999", OrderStatus.PAID);

        when(rediscache.findById(order.getId())).thenReturn(Optional.of(order));

        Optional<Order> serviceOrder = service.findById(order.getId());

        assertEquals(order, serviceOrder.orElseThrow());
        verify(postgresRepository, never()).findOrderById(order.getId());
        verify(rediscache, never()).put(order);
    }

    @Test
    public void loadsFromRepositoryAndCachesOrderWhenRedisMisses() {
        OrderLookupService service = new OrderLookupService(postgresRepository, rediscache);
        Order order = new Order("order-102", OrderStatus.PAID);
        when(rediscache.findById("order-102")).thenReturn(Optional.empty());
        when(postgresRepository.findOrderById("order-102")).thenReturn(Optional.of(order));

        Optional<Order> serviceOrder = service.findById(order.getId());
        assertNotNull(serviceOrder);
        assertEquals(order, serviceOrder.orElseThrow());
        verify(postgresRepository).findOrderById(order.getId());
        verify(rediscache).put(order);
    }

    @Test
    public void returnsRepositoryOrderWhenCacheReadFails() {
        Order order = new Order("order-303", OrderStatus.PAID);
        OrderLookupService service = new OrderLookupService(postgresRepository, rediscache);
        when(rediscache.findById(order.getId()))
                .thenThrow(new RedisConnectionFailureException(
                        "Redis is unavailable",
                        new RuntimeException()));

        when(postgresRepository.findOrderById(order.getId()))
                .thenReturn(Optional.of(order));

        Optional<Order> returnedOrder = service.findById(order.getId());
        assertEquals(order.getId(), returnedOrder.orElseThrow().getId());
        assertEquals(OrderStatus.PAID, returnedOrder.orElseThrow().getStatus());

        verify(postgresRepository).findOrderById(order.getId());
    }

    @Test
    public void returnsRepositoryOrderWhenCacheWriteFails() {
        Order order = new Order("order-303", OrderStatus.PAID);
        OrderLookupService service = new OrderLookupService(postgresRepository, rediscache);
        when(rediscache.findById(order.getId())).thenReturn(Optional.empty());
        when(postgresRepository.findOrderById(order.getId())).thenReturn(Optional.of(order));
        doThrow(new RedisConnectionFailureException(
                "Redis is unavailable",
                new RuntimeException()))
                .when(rediscache)
                .put(order);
        Optional<Order> returnOrder = service.findById(order.getId());
        assertNotNull(returnOrder.get());
        assertEquals(order.getId(), returnOrder.get().getId());
        assertEquals(order.getStatus(), returnOrder.get().getStatus());
        verify(postgresRepository).findOrderById(order.getId());
        verify(rediscache).put(order);
    }

    @Test
    public void doesNotThrowWhenCacheEvictionFails() {
        OrderLookupService service = new OrderLookupService(postgresRepository, rediscache);
        String orderId = "order-404";
        doThrow(new RedisConnectionFailureException(
                "Redis is unavailable",
                new RuntimeException()))
                .when(rediscache)
                .evict(orderId);
        assertDoesNotThrow(()->service.evict(orderId));
        verify(rediscache).evict(orderId);
    }
}
