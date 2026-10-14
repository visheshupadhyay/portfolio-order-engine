package com.vishesh.orderengine.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import com.vishesh.orderengine.cache.OrderCache;
import com.vishesh.orderengine.message.OrderPaidMessage;
import com.vishesh.orderengine.order.Order;
import com.vishesh.orderengine.order.OrderStatus;
import com.vishesh.orderengine.outbox.OutboxEventDeliveryWorker;
import com.vishesh.orderengine.outbox.OutboxEventRowMapper;
import com.vishesh.orderengine.outbox.OutboxEventStatus;
import com.vishesh.orderengine.security.JwtTokenService;

import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("postgres")
public class OrderPaymentReliabilityEndToEndTest extends AbstractPostgresIntegrationTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtTokenService jwtTokenService;
    @Autowired
    private OutboxEventDeliveryWorker outboxEventDeliveryWorker;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private OrderCache orderCache;

    private String savedOrderId;
    private Long savedEventId;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private static final KafkaContainer kafkaContainer = new KafkaContainer(
            DockerImageName.parse("apache/kafka:4.3.1"));

    @SuppressWarnings("resource")
    private static final GenericContainer<?> redisContainer = new GenericContainer<>(
            DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    private RequestPostProcessor readerToken() {
        String token = jwtTokenService.issue(
                "order-reader",
                Set.of("ROLE_ORDER_READER"));

        return request -> {
            request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
            return request;
        };
    }

    private RequestPostProcessor writerToken() {
        String token = jwtTokenService.issue(
                "order-writer",
                Set.of("ROLE_ORDER_WRITER"));

        return request -> {
            request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
            return request;
        };
    }

    @DynamicPropertySource
    public static void configureKafka(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", () -> kafkaContainer.getBootstrapServers());
        registry.add("spring.kafka.listener.auto-startup", () -> true);
        registry.add("spring.data.redis.host", redisContainer::getHost);
        registry.add("spring.data.redis.port", () -> redisContainer.getMappedPort(6379));
    }

    static {
        kafkaContainer.start();
        redisContainer.start();
    }

    @Test
    public void startsTemporaryKafkaAndRedisContainers() {
        assertTrue(kafkaContainer.isRunning());
        assertTrue(redisContainer.isRunning());
    }

    @AfterEach
    public void cleanup() {
        if (savedOrderId != null) {
            jdbcTemplate.update("DELETE FROM outbox_events WHERE order_id = ?", savedOrderId);
            jdbcTemplate.update("DELETE FROM orders WHERE id = ?", savedOrderId);
            orderCache.evict(savedOrderId);
        }
    }

    @Test
    public void createsPaysPublishesAndRefreshesCachedOrder() throws Exception {
        savedOrderId = "e2e-order-" + UUID.randomUUID();

        String requestBody = "{\"id\":\"" + savedOrderId + "\"}";
        mockMvc.perform(post("/orders").with(writerToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/orders/" + savedOrderId).with(readerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CREATED"));

        assertEquals(OrderStatus.CREATED, orderCache.findById(savedOrderId).orElseThrow().getStatus());

        mockMvc.perform(post("/orders/" + savedOrderId + "/pay")
                .with(writerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));

        assertTrue(orderCache.findById(savedOrderId).isEmpty());
        savedEventId = jdbcTemplate.queryForObject("Select * from outbox_events where order_id=?",
                new OutboxEventRowMapper(), savedOrderId).id();

        outboxEventDeliveryWorker.deliverReadyEvents(LocalDateTime.now().plusSeconds(1), 10);

        assertEquals(OutboxEventStatus.SENT,
                jdbcTemplate.queryForObject("Select * from outbox_events where order_id=?",
                        new OutboxEventRowMapper(), savedOrderId).status());

        mockMvc.perform(get("/orders/" + savedOrderId).with(readerToken())).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));

        assertEquals(OrderStatus.PAID, orderCache.findById(savedOrderId).orElseThrow().getStatus());
    }
}