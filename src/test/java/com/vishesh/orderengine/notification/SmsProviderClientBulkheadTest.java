package com.vishesh.orderengine.notification;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

public class SmsProviderClientBulkheadTest {
    @Test
    public void rejectsSecondSmsCallWhenBulkheadIsFull() throws Exception {
        CountDownLatch firstRequestStarted = new CountDownLatch(1);
        CountDownLatch allowFirstRequestToFinish = new CountDownLatch(1);
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<Void> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(202);

        when(httpClient.send(
                any(HttpRequest.class),
                any(HttpResponse.BodyHandler.class)))
                .thenAnswer(invocation -> {
                    firstRequestStarted.countDown();
                    allowFirstRequestToFinish.await();
                    return response;
                });

        BulkheadConfig bulkheadConfig = BulkheadConfig.custom()
                .maxConcurrentCalls(1)
                .maxWaitDuration(Duration.ZERO)
                .build();
        BulkheadRegistry registry = BulkheadRegistry.of(bulkheadConfig);
        Bulkhead bulkhead = registry.bulkhead("sms-provider-test");
        URI providerBaseUri = URI.create("http://sms-provider.test");
        SmsProviderClient client = new SmsProviderClient(httpClient, providerBaseUri,
                new ObjectMapper(),
                Duration.ofSeconds(2),
                CircuitBreaker.ofDefaults("test"),
                bulkhead,
                new SmsDeliveryMetrics(new SimpleMeterRegistry()));
        ExecutorService threadExecutorService = Executors.newSingleThreadExecutor();

        try {
            Future<?> firstCall = threadExecutorService.submit(
                    () -> client.send("Order-Engine", "test", "event-42"));

            // Do not try the second call until the first call is definitely holding the one
            // slot.
            assertTrue(firstRequestStarted.await(1, TimeUnit.SECONDS));

            SmsProviderUnavailableException exception = assertThrows(SmsProviderUnavailableException.class,
                    () -> client.send("Order-Engine", "test", "event-43"));

            assertInstanceOf(BulkheadFullException.class, exception.getCause());
            // The rejected second call never reaches the outbound HTTP layer.
            verify(httpClient, times(1)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));

            allowFirstRequestToFinish.countDown();
            firstCall.get(1, TimeUnit.SECONDS);
        } finally {
            // Prevent a blocked first task from leaking if an assertion above fails.
            allowFirstRequestToFinish.countDown();
            threadExecutorService.shutdownNow();
        }

    }
}
