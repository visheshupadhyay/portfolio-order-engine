package com.vishesh.orderengine.config;

import java.net.URI;
import java.net.http.HttpClient;
/*
 * Keeps the few beans that need custom construction or runtime configuration:
 * notifiers, the property-backed input Path, and startup work. Spring Boot now
 * performs component scanning and property loading automatically.
 */
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;

import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import com.vishesh.orderengine.importer.OrderImportService;
import com.vishesh.orderengine.notification.AbstractNotifier;
import com.vishesh.orderengine.notification.EmailNotifier;
import com.vishesh.orderengine.notification.SmsDeliveryMetrics;
import com.vishesh.orderengine.notification.SmsNotifier;
import com.vishesh.orderengine.notification.SmsProviderClient;
import com.vishesh.orderengine.security.JwtAuthenticationFilter;
import com.vishesh.orderengine.security.JwtProperties;
import com.vishesh.orderengine.security.JwtTokenService;

import tools.jackson.databind.ObjectMapper;

@Configuration
public class OrderEngineConfiguration {
    @Bean
    @Primary
    public AbstractNotifier emailNotifier() {
        return new EmailNotifier("orders@example.com");
    }

    @Bean
    public AbstractNotifier smsNotifier(SmsProviderClient smsProviderClient) {
        return new SmsNotifier("OrderEngine", smsProviderClient);
    }

    // Spring reads this value from application.properties, so each environment
    // can choose its input file without changing Java code.
    @Bean
    public Path orderInputFile(@Value("${order.input-file}") String inputFile) {
        return Path.of(inputFile);
    }

    /*
     * Boot invokes this runner only after the context has created and injected
     * its beans. The runner starts the use case; OrderImportService owns its logic.
     */
    @Bean
    public CommandLineRunner importConfiguredOrdersAtStartup(OrderImportService orderImportService,
            Path orderInputFile) {
        return args -> orderImportService.importAndSave(orderInputFile);
    }

    @Bean
    public HttpClient smsProviderHttpClient() {
        return HttpClient.newHttpClient();
    }

    @Bean
    public Clock systemClock() {
        // Production uses UTC; tests can inject a fixed Clock to prove expiry behavior.
        return Clock.systemUTC();
    }

    @Bean
    public JwtTokenService jwtTokenService(JwtProperties jwtProperties, Clock clock) {
        // The secret is read from external configuration, not embedded in Java code.
        return new JwtTokenService(jwtProperties.issuer(), jwtProperties.base64Secret(), jwtProperties.accessTokenTtl(),
                clock);

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

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(JwtTokenService jwtTokenService) {
        // SecurityConfiguration inserts this managed filter into Spring's request
        // chain.
        return new JwtAuthenticationFilter(jwtTokenService);
    }
}
