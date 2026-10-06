package com.vishesh.orderengine.message;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import com.vishesh.orderengine.integration.AbstractPostgresIntegrationTest;
import com.vishesh.orderengine.notification.OrderPaidNotificationService;
import com.vishesh.orderengine.order.Order;

import tools.jackson.databind.ObjectMapper;

/*
 * This is an end-to-end Kafka reliability test.
 *
 * It uses real, temporary Docker containers instead of a mocked Kafka broker:
 *
 *   test sends broken text to "order-paid"
 *       -> the real Spring Kafka listener cannot read it as JSON
 *       -> @RetryableTopic retries it
 *       -> Spring moves it to "order-paid.DLT"
 *       -> this test reads the DLT and proves the same text arrived there.
 *
 * AbstractPostgresIntegrationTest starts PostgreSQL as well, because
 * @SpringBootTest starts the whole application and its PostgreSQL beans.
 */
@SpringBootTest
@ActiveProfiles("postgres")
public class KafkaRetryAndDeadLetterIntegrationTest extends AbstractPostgresIntegrationTest {
    @Autowired
    private ObjectMapper objectMapper;

    /*
     * The application normally calls the real notification service, which may
     * send an SMS. In this test class Spring replaces that final step with a
     * Mockito mock. Kafka, the listener, retries, and DLT routing stay real;
     * only SMS delivery becomes controllable and safe for a test.
     */
    @MockitoBean
    private OrderPaidNotificationService notificationService;
    /*
     * One real Kafka broker for this whole test class. Testcontainers starts
     * apache/kafka in Docker and chooses a safe random host port, so this test
     * does not require the developer's Compose Kafka container to be running.
     *
     * The container intentionally lives for the test JVM, hence "resource" is
     * suppressed just as it is for the shared PostgreSQL container.
     */
    @SuppressWarnings("resource")
    private static final KafkaContainer kafkaContainer = new KafkaContainer(
            DockerImageName.parse("apache/kafka:4.3.1"));

    /*
     * Spring creates this real Kafka sender after it receives the temporary
     * broker address below. The test uses it to place a deliberately broken
     * message on the normal order-paid topic.
     */
    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    static {
        // Start Kafka before Spring builds the application context. Spring needs
        // a running broker address when it creates listeners and KafkaTemplate.
        kafkaContainer.start();
    }

    @DynamicPropertySource
    public static void configureKafka(DynamicPropertyRegistry registry) {
        // Replace the normal localhost:9092 setting with Testcontainers' random
        // address, for example localhost:49183. Hard-coding a port would make
        // parallel runs and CI unreliable.
        registry.add("spring.kafka.bootstrap-servers", () -> kafkaContainer.getBootstrapServers());

        // Ordinary Spring tests disable Kafka listeners so they do not need a
        // broker. This test specifically needs the real consumer to run and
        // perform retries, so it overrides that test-only default.
        registry.add("spring.kafka.listener.auto-startup", () -> true);
    }

    @Test
    public void startsTemporaryKafkaAndConfiguresSpringKafka() {
        // This small smoke test proves two separate things: Docker started the
        // temporary Kafka broker, and Spring successfully configured a sender
        // that knows how to talk to that broker.
        assertNotNull(kafkaTemplate);
        assertTrue(kafkaContainer.isRunning());
    }

    @Test
    public void movesMalformedMessageToDeadLetterTopicAfterRetries() throws Exception {
        // Kafka stores progress per consumer group. A new inspector group gives
        // this test its own bookmark, independent of the application's group
        // and independent of earlier test runs.
        String consumerGroupId = "dlt-inspector-" + UUID.randomUUID();

        // This is deliberately NOT JSON. The application listener expects an
        // OrderPaidMessage JSON object, so parsing this text must fail.
        String malformedJSON = "not-valid-json-";

        // Build connection settings for a test-only Kafka reader. The argument
        // order is: broker address, inspector group ID, then auto-commit flag.
        // false means this short-lived inspector will not save a Kafka bookmark.
        Map<String, Object> consumerProperties = KafkaTestUtils.consumerProps(
                kafkaContainer.getBootstrapServers(), consumerGroupId, false);

        // A brand-new inspector group has no previous position. "earliest" says
        // it may read the oldest available DLT record rather than waiting only
        // for records created after it starts.
        consumerProperties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        // This consumer is NOT part of the production application. It is only a
        // temporary test inspector which opens the DLT and checks its contents.
        // try-with-resources closes it cleanly after the assertion.
        try (Consumer<String, String> dltConsumer = new KafkaConsumer<>(
                consumerProperties,
                new StringDeserializer(),
                new StringDeserializer())) {

            // Start watching the problem-parcels shelf before we send the bad
            // parcel. This topic is created by @RetryableTopic using the
            // configured ".DLT" suffix.
            dltConsumer.subscribe(List.of("order-paid.DLT"));

            // Put the broken text on the normal topic. get() waits until Kafka
            // confirms it accepted the original record; it does NOT wait for
            // the application's retries or DLT processing.
            kafkaTemplate.send("order-paid", malformedJSON).get();

            // Wait up to 15 seconds while Spring performs the configured retry
            // attempts. When they are exhausted, Spring forwards the original
            // broken text to order-paid.DLT. This helper returns that one DLT
            // record, or fails the test if it never arrives in time.
            ConsumerRecord<String, String> dltRecord = KafkaTestUtils.getSingleRecord(
                    dltConsumer,
                    "order-paid.DLT",
                    Duration.ofSeconds(15));

            // The final proof: the exact text placed on the normal topic was
            // preserved and moved to the dead-letter topic after retries.
            assertEquals(malformedJSON, dltRecord.value());
        }
    }

    @Test
    public void retriesTemporaryNotificationFailureAndEventuallySucceeds() throws Exception {
        /*
         * This is a VALID order-paid message. Unlike the DLT test above, JSON
         * parsing will work. The failure we simulate is later in the journey:
         * the notification/SMS step temporarily fails.
         */
        String orderId = "temporary-retry-order";
        long eventId = 10L;
        OrderPaidMessage orderPaidMessage = new OrderPaidMessage(eventId, orderId);
        String validJSON = objectMapper.writeValueAsString(orderPaidMessage);

        /*
         * Tell the fake notification service to behave like a temporarily
         * unavailable SMS provider:
         *
         * first call -> throw an exception, so Spring schedules a retry;
         * second call -> do nothing, which represents a successful SMS send.
         *
         * The chained form matters: two separate Mockito stubs would cause the
         * later one to replace the earlier one instead of creating this story.
         */
        doThrow(new RuntimeException("SMS temporarily unavailable"))
                .doNothing()
                .when(notificationService)
                .notifyOrderPaid(any(Order.class), eq(eventId));

        // Place valid JSON onto real temporary Kafka. get() waits only until
        // Kafka accepts the record; the application processes it asynchronously.
        kafkaTemplate.send("order-paid", validJSON).get();

        // A captor is an empty box that Mockito fills with the real Order
        // arguments used by the background Kafka listener.
        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);

        /*
         * The listener runs on another thread, so ordinary verify(...) might
         * run before Kafka has delivered anything. timeout(...) waits for the
         * expected two calls:
         *
         * original delivery fails -> retry delivery succeeds.
         */
        verify(notificationService, timeout(15_000).times(2))
                .notifyOrderPaid(captor.capture(), eq(eventId));
        List<Order> capturedOrders = captor.getAllValues();

        // Both attempts must concern the same order. This proves a retry did
        // not accidentally create or process a different order message.
        assertEquals(2, capturedOrders.size());

        assertEquals(orderId, capturedOrders.get(0).getId());
        assertEquals(orderId, capturedOrders.get(1).getId());
    }

    @Test
    public void publishesOrderPaidMessageToRealKafka() {
        /*
         * This test uses its own empty topic instead of "order-paid". That
         * prevents records from the consumer retry/DLT tests from appearing in
         * this test's result. Kafka will create this temporary topic when the
         * real publisher first sends to it.
         */
        String topicName = "publisher-test-" + UUID.randomUUID();

        // Every Kafka consumer group owns an independent bookmark. A unique
        // inspector group ensures this test reads only for itself.
        String inspectorId = "publisher-inspector-" + UUID.randomUUID();

        // This is the Java object our production publisher must turn into JSON
        // and store in the real Kafka topic.
        OrderPaidMessage message = new OrderPaidMessage(101L, "publish-order-501");

        /*
         * Create the real production publisher, not a Mockito fake. We give it
         * the unique topic, the real Spring ObjectMapper, and the real
         * KafkaTemplate connected to the Testcontainers broker.
         */
        KafkaOrderPaidEventPublisher publisher = new KafkaOrderPaidEventPublisher(topicName, objectMapper,
                kafkaTemplate);

        // Configure a temporary test-only reader. "earliest" is safe because
        // this unique topic has no older records belonging to another test.
        Map<String, Object> consumerProperties = KafkaTestUtils.consumerProps(kafkaContainer.getBootstrapServers(),
                inspectorId, false);
        consumerProperties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        // This is not an application consumer. It is the test's inspector: it
        // opens the destination topic and checks what the real publisher wrote.
        try (Consumer<String, String> dltConsumer = new KafkaConsumer<>(
                consumerProperties,
                new StringDeserializer(),
                new StringDeserializer())) {

            // Begin watching the unique topic before publishing the message.
            dltConsumer.subscribe(List.of(topicName));

            // This is the actual method under test. Inside publish(...), the
            // production code serializes the OrderPaidMessage and waits for
            // Kafka to acknowledge that it stored the record.
            publisher.publish(message);

            // Wait for the one real record stored in Kafka. The helper fails if
            // it does not arrive within ten seconds.
            ConsumerRecord<String, String> dltRecord = KafkaTestUtils.getSingleRecord(
                    dltConsumer,
                    topicName,
                    Duration.ofSeconds(10));

            // Rebuild a Java object from Kafka's stored JSON. These assertions
            // prove both important fields survived the full publisher -> Kafka
            // -> consumer round trip unchanged.
            OrderPaidMessage o = objectMapper.readValue(dltRecord.value(), OrderPaidMessage.class);
            assertEquals(101L, o.eventId());
            assertEquals("publish-order-501", o.orderId());
        }
    }

    @Test
    public void processesFutureMessageWithUnknownField()throws Exception {
        String json = "{\"eventId\":11,\"orderId\":\"future-schema-order\",\"paymentMethod\":\"CARD\"}";
        kafkaTemplate.send("order-paid", json).get();
        ArgumentCaptor<Order> captorOrder = ArgumentCaptor.forClass(Order.class);
        verify(notificationService, timeout(10_000).times(1)).notifyOrderPaid(captorOrder.capture(), eq(11L));
        Order capturedOrder = captorOrder.getValue();
        assertEquals("future-schema-order", capturedOrder.getId());
    }
}
