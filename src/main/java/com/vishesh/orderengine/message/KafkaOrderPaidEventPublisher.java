package com.vishesh.orderengine.message;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class KafkaOrderPaidEventPublisher implements OrderPaidEventPublisher {
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String topic;

    public KafkaOrderPaidEventPublisher(@Value("${messaging.order-paid-topic}") String topic,
            ObjectMapper objectMapper,
            KafkaTemplate<String, String> kafkaTemplate) {

        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.topic = topic;
    }

    @Override
    public void publish(OrderPaidMessage message) {
        try {
            // Kafka stores bytes/text, not Java objects. Convert our small event
            // object to JSON before sending it to the order-paid topic.
            String jsonText = objectMapper.writeValueAsString(message);

            // The order ID is the Kafka message key. Kafka keeps records with the
            // same key in the same partition, preserving their order for one order.
            CompletableFuture<SendResult<String, String>> future = kafkaTemplate.send(topic, message.orderId(),
                    jsonText);

            // The outbox row must become SENT only after Kafka confirms it accepted
            // the event. Waiting here lets a broker failure reach the outbox worker,
            // which will keep the durable row pending and retry it later.
            future.get();
        } catch (JacksonException e) {
            throw new IllegalStateException("JSON could not be created",e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Kafka rejected or could not complete delivery",e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Kafka publishing was interrupted",e);
        }

    }

}
