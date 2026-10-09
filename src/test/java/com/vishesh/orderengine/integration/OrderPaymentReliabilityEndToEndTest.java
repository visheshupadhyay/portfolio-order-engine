package com.vishesh.orderengine.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import com.vishesh.orderengine.cache.OrderCache;
import com.vishesh.orderengine.message.OrderPaidMessage;
import com.vishesh.orderengine.notification.OrderPaidNotificationService;
import com.vishesh.orderengine.notification.SmsProviderClient;
import com.vishesh.orderengine.notification.SmsProviderUnavailableException;
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

    @MockitoBean
    private SmsProviderClient smsProviderClient;

    @MockitoSpyBean
    private OrderPaidNotificationService notificationService;

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
        if (savedEventId != null) {
            jdbcTemplate.update("DELETE FROM processed_notification_events WHERE event_id = ?", savedEventId);
        }

        if (savedOrderId != null) {
            jdbcTemplate.update("DELETE FROM outbox_events WHERE order_id = ?", savedOrderId);
            jdbcTemplate.update("DELETE FROM orders WHERE id = ?", savedOrderId);
            orderCache.evict(savedOrderId);
        }
    }

    @Test
    public void createsPaysPublishesNotifiesAndRefreshesCachedOrder() throws Exception {
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

        assertEquals(OutboxEventStatus.SENT, jdbcTemplate.queryForObject("Select * from outbox_events where order_id=?",
                new OutboxEventRowMapper(), savedOrderId).status());

        verify(smsProviderClient, timeout(15_000).times(1)).send("OrderEngine", "Order is paid orderID:" + savedOrderId,
                String.valueOf(savedEventId));

        mockMvc.perform(get("/orders/" + savedOrderId).with(readerToken())).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));

        assertEquals(OrderStatus.PAID, orderCache.findById(savedOrderId).orElseThrow().getStatus());
    }

    @Test
    public void doesNotSendSecondSmsWhenKafkaDeliversSameEventAgain() throws Exception {
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

        assertEquals(OutboxEventStatus.SENT, jdbcTemplate.queryForObject("Select * from outbox_events where order_id=?",
                new OutboxEventRowMapper(), savedOrderId).status());

        verify(notificationService, timeout(15_000).times(1)).notifyOrderPaid(any(Order.class), eq(savedEventId));
        verify(smsProviderClient, timeout(15_000).times(1)).send("OrderEngine", "Order is paid orderID:" + savedOrderId,
                String.valueOf(savedEventId));

        OrderPaidMessage duplicateMessage = new OrderPaidMessage(savedEventId, savedOrderId);

        String duplicateJson = objectMapper.writeValueAsString(duplicateMessage);

        kafkaTemplate.send("order-paid", duplicateJson).get();
        verify(notificationService, timeout(15_000).times(2)).notifyOrderPaid(any(Order.class), eq(savedEventId));

        verify(smsProviderClient).send("OrderEngine", "Order is paid orderID:" + savedOrderId,
                String.valueOf(savedEventId));

    }

    @Test
    public void retriesTemporarySmsFailureWithoutUndoingPaidOrder() throws Exception {
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

        doThrow(new SmsProviderUnavailableException("SMS temporarily unavailable"))
                .doNothing()
                .when(smsProviderClient)
                .send(eq("OrderEngine"), eq("Order is paid orderID:" + savedOrderId), eq(String.valueOf(savedEventId)));

        outboxEventDeliveryWorker.deliverReadyEvents(LocalDateTime.now().plusSeconds(1), 10);

        assertEquals(OutboxEventStatus.SENT,
                jdbcTemplate.queryForObject(
                        "SELECT * FROM outbox_events WHERE order_id = ?", new OutboxEventRowMapper(), savedOrderId)
                        .status());

        verify(notificationService, timeout(15_000).times(2)).notifyOrderPaid(any(Order.class), eq(savedEventId));

        verify(smsProviderClient, timeout(15_000).times(2)).send("OrderEngine", "Order is paid orderID:" + savedOrderId,
                String.valueOf(savedEventId));

        String storedStatus = jdbcTemplate.queryForObject("SELECT status FROM orders WHERE id = ?", String.class,
                savedOrderId);

        assertEquals(OrderStatus.PAID.name(), storedStatus);

        Long processedEventCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM processed_notification_events WHERE event_id = ?", Long.class,
                savedEventId);

        assertEquals(1L, processedEventCount);
    }

    @Test
    public void movesMalformedKafkaMessageToDeadLetterTopic() throws Exception {
        String malformedJson = "e2e-not-valid-json-" + UUID.randomUUID();
        String inspectorGroupId = "e2e-dlt-inspector-" + UUID.randomUUID();

        Map<String, Object> consumerProperties = KafkaTestUtils.consumerProps(kafkaContainer.getBootstrapServers(),
                inspectorGroupId, false);

        consumerProperties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        try (Consumer<String, String> dltConsumer = new KafkaConsumer<>(
                consumerProperties,
                new StringDeserializer(),
                new StringDeserializer())) {


            dltConsumer.subscribe(List.of("order-paid.DLT"));

            kafkaTemplate.send("order-paid", malformedJson).get();


            ConsumerRecord<String, String> dltRecord = KafkaTestUtils.getSingleRecord(
                    dltConsumer,
                    "order-paid.DLT",
                    Duration.ofSeconds(15));

            assertEquals(malformedJson, dltRecord.value());

            verifyNoInteractions(notificationService, smsProviderClient);
        }
    }
}