package com.vishesh.orderengine.message;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.eq;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import tools.jackson.databind.ObjectMapper;

public class KafkaOrderPaidEventPublisherTest {
    @SuppressWarnings("unchecked")
    private KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
    private ObjectMapper objectMapper = new ObjectMapper();
    private KafkaOrderPaidEventPublisher publisher = new KafkaOrderPaidEventPublisher("order-paid", objectMapper,
            kafkaTemplate);

    @SuppressWarnings("unchecked")
    @Test
    public void publishesOrderPaidMessageAsJsonToConfiguredTopic() {
        OrderPaidMessage message1 = new OrderPaidMessage(45L, "order-501");
        SendResult<String, String> successfulSend = mock(SendResult.class);
        when(kafkaTemplate.send(
                anyString(),
                anyString(),
                anyString()))
                .thenReturn(CompletableFuture.completedFuture(successfulSend));

        publisher.publish(message1);
        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq("order-paid"), eq("order-501"), jsonCaptor.capture());
        String sentJson = jsonCaptor.getValue();
        OrderPaidMessage message2 = objectMapper.readValue(sentJson, OrderPaidMessage.class);
        assertEquals("order-501", message2.orderId());
        assertEquals(45L, message2.eventId());
    }

    @Test
    public void throwsWhenKafkaDeliveryFails() {
        OrderPaidMessage message = new OrderPaidMessage(45L, "order-501");
        when(kafkaTemplate.send(
                anyString(),
                anyString(),
                anyString()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Kafka Unavailable")));

        IllegalStateException exception = assertThrows(IllegalStateException.class,()->publisher.publish(message));
        assertEquals("Kafka rejected or could not complete delivery", exception.getMessage());
    }
}
