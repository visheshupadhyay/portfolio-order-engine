package com.vishesh.orderengine.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafkaRetryTopic;

/**
 * Turns on Spring Kafka's infrastructure for @RetryableTopic annotations.
 * Without this configuration, the consumer could receive messages but Spring
 * would not create/use the retry and dead-letter-topic flow we rely on.
 */
@Configuration 
@EnableKafkaRetryTopic 
public class KafkaRetryConfiguration {
    
}
