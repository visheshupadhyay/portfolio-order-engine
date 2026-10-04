package com.vishesh.orderengine.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.vishesh.orderengine.integration.AbstractPostgresIntegrationTest;
import com.vishesh.orderengine.order.Order;
import com.vishesh.orderengine.order.OrderStatus;
import com.vishesh.orderengine.order.OrderLookupService;
import com.vishesh.orderengine.order.OrderRepository;

@SpringBootTest
@Testcontainers
public class RedisOrderCacheIntegrationTest extends AbstractPostgresIntegrationTest {
    // This temporary Redis is independent of any Redis started through Docker Compose.
    @Container
    @SuppressWarnings("resource")
    private static final GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);
    @Autowired
    private OrderCache orderCache;

    @DynamicPropertySource
    public static void configureRedis(DynamicPropertyRegistry registry) {
        // Spring receives Docker's random mapped port, so parallel/local ports do not clash.
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Test
    public void storesAndReadsCachedOrder() {
        String orderId = UUID.randomUUID().toString();
        try {
            Order order = new Order(orderId, OrderStatus.PAID);
            orderCache.put(order);
            Optional<Order> returnedOrder = orderCache.findById(orderId);
            assertEquals(order.getId(), returnedOrder.orElseThrow().getId());
            assertEquals(OrderStatus.PAID, returnedOrder.orElseThrow().getStatus());
        } finally {
            orderCache.evict(orderId);
        }

    }

    @Test
    public void returnsEmptyWhenRedisDoesNotContainOrder() {
        String missingOrderId = "missing-order-" + UUID.randomUUID().toString();
        Optional<Order> returnedOrder = orderCache.findById(missingOrderId);
        assertTrue(returnedOrder.isEmpty());

    }

    @Test
    public void evictsCachedOrder() {
        String orderId = UUID.randomUUID().toString();
        try {
            Order order = new Order(orderId, OrderStatus.PAID);
            orderCache.put(order);
            Optional<Order> returnedOrder = orderCache.findById(orderId);
            assertTrue(returnedOrder.isPresent());

            orderCache.evict(orderId);
            Optional<Order> returnOrderAfterEviction = orderCache.findById(orderId);
            assertTrue(returnOrderAfterEviction.isEmpty());
        } finally {
            orderCache.evict(orderId);
        }
    }

    @Test
    public void usesRealRedisOnSecondLookupInsteadOfRepository() {
        String orderId = UUID.randomUUID().toString();
        Order order = new Order(orderId);
        OrderRepository repository = mock(OrderRepository.class);
        when(repository.findOrderById(orderId)).thenReturn(Optional.of(order));
        OrderLookupService service = new OrderLookupService(repository, orderCache);
        try {
            // First lookup misses Redis, so the mocked repository supplies an order and Redis stores it.
            Optional<Order> returnOrder1 = service.findById(orderId);
            verify(repository).findOrderById(orderId);
            clearInvocations(repository);
            // Clear only Mockito history. The real Redis value remains for the second lookup.
            Optional<Order> returnOrder2 = service.findById(orderId);
            verify(repository,never()).findOrderById(orderId);
        }
        finally {
            // A shared Testcontainer can outlive one test method, so remove this test's key explicitly.
            orderCache.evict(orderId);
        }
    }

}
