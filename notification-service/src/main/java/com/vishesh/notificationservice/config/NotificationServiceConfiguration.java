package com.vishesh.notificationservice.config;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.vishesh.notificationservice.notification.SmsDeliveryMetrics;
import com.vishesh.notificationservice.notification.SmsProviderClient;

import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import tools.jackson.databind.ObjectMapper;

@Configuration 
public class NotificationServiceConfiguration {
    @Bean 
    public HttpClient smsProviderHttpClient() {
        return HttpClient.newHttpClient();
    }

    @Bean
    public SmsProviderClient smsProviderClient(HttpClient httpClient, ObjectMapper objectMapper,
            @Value("${notification.sms.provider-base-url}") String baseURL,
            @Value("${notification.sms.request-timeout}") Duration requestTimeout,
            CircuitBreakerRegistry circuitBreakerRegistry,
            BulkheadRegistry bulkheadRegistry,
            SmsDeliveryMetrics smsDeliveryMetrics) {

        // The registry names must match application.properties. Resilience4j
        // reads its thresholds from configuration; this bean only wires the
        // configured guards and observability counters into the HTTP adapter.
        return new SmsProviderClient(
                httpClient,
                URI.create(baseURL),
                objectMapper,
                requestTimeout,
                circuitBreakerRegistry.circuitBreaker("smsProvider"),
                bulkheadRegistry.bulkhead("smsProvider"),
                smsDeliveryMetrics);
    }
}
