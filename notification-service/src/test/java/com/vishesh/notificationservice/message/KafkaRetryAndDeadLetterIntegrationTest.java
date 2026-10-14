package com.vishesh.notificationservice.message;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import com.vishesh.notificationservice.integration.AbstractNotificationServiceIntegrationTest;

@SpringBootTest
public class KafkaRetryAndDeadLetterIntegrationTest extends AbstractNotificationServiceIntegrationTest {
    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Test
    public void movesMalformedMessageToDeadLetterTopicAfterRetries() throws Exception {
        String uniqueInspectorId = UUID.randomUUID().toString();
        String malformedJSON = "not-valid-json-" + UUID.randomUUID();
        Map<String, Object> consumerProperties = KafkaTestUtils.consumerProps(
                kafka.getBootstrapServers(), uniqueInspectorId, false);
        consumerProperties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        try(Consumer<String, String> dltConsumer = new KafkaConsumer<>(
                consumerProperties,
                new StringDeserializer(),
                new StringDeserializer())){

            dltConsumer.subscribe(List.of("order-paid.DLT"));

            kafkaTemplate.send("order-paid", malformedJSON).get();

            ConsumerRecord<String, String> dltRecord = KafkaTestUtils.getSingleRecord(
                    dltConsumer,
                    "order-paid.DLT",
                    Duration.ofSeconds(15));

            assertEquals(malformedJSON, dltRecord.value());
        }
    }

}
