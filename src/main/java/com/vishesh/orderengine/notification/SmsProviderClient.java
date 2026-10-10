package com.vishesh.orderengine.notification;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Map;

import tools.jackson.core.JacksonException;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import tools.jackson.databind.ObjectMapper;

/*
 * Outbound HTTP adapter for the SMS provider. WireMock tests protect this
 * request contract without contacting a real provider during Maven tests.
 */
public class SmsProviderClient {
    private final HttpClient httpClient;
    private final URI providerBaseUri;
    private final ObjectMapper objectMapper;
    private final Duration requestTimeout;
    private final CircuitBreaker circuitBreaker;
    private final Bulkhead bulkhead;
    private final SmsDeliveryMetrics smsDeliveryMetrics;

    public SmsProviderClient(HttpClient httpClient,
            URI providerBaseUri,
            ObjectMapper objectMapper,
            Duration requestTimeout,
            CircuitBreaker circuitBreaker,
            Bulkhead bulkhead,
            SmsDeliveryMetrics smsDeliveryMetrics) {

        this.httpClient = httpClient;
        this.providerBaseUri = providerBaseUri;
        this.objectMapper = objectMapper;
        this.requestTimeout = requestTimeout;
        this.circuitBreaker = circuitBreaker;
        this.bulkhead = bulkhead;
        this.smsDeliveryMetrics = smsDeliveryMetrics;
    }

    public void send(String senderName, String message, String idempotencyKey) {
        // When OPEN, the breaker rejects this call immediately without contacting the
        // provider.
        String jsontext = serializeRequest(senderName, message);

        try {
            bulkhead.executeRunnable(
                    () -> circuitBreaker.executeRunnable(() -> sendToProvider(jsontext, idempotencyKey)));
            // Count success only after the provider returns 202 through both
            // resilience guards. Building a request is not a successful hand-off.
            smsDeliveryMetrics.recordSent();
        } catch (BulkheadFullException exception) {
            smsDeliveryMetrics.recordFailed();
            throw new SmsProviderUnavailableException("SMS provider is temporarily unavailable", exception);

        } catch (CallNotPermittedException exception) {
            smsDeliveryMetrics.recordFailed();
            throw new SmsProviderUnavailableException("SMS provider is temporarily unavailable", exception);
        } catch (SmsProviderUnavailableException exception) {
            smsDeliveryMetrics.recordFailed();
            throw exception;
        }
    }

    private String serializeRequest(String senderName, String message) {
        try {
            return objectMapper.writeValueAsString(Map.of("senderName", senderName, "message", message));
        } catch (JacksonException exception) {
            throw new IllegalStateException("Could not serialize SMS request", exception);
        }

    }

    private void sendToProvider(String jsontext, String idempotencyKey) {
        try {

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(providerBaseUri.resolve("/sms"))
                    .timeout(requestTimeout)
                    .header("Content-Type", "application/json")
                    .header("Idempotency-Key", idempotencyKey)
                    .POST(HttpRequest.BodyPublishers.ofString(jsontext))
                    .build();

            HttpResponse<Void> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.discarding());

            // A provider acknowledgement is the only successful delivery handoff.
            if (response.statusCode() != 202) {
                throw new SmsProviderUnavailableException(
                        "SMS provider returned unexpected status: " + response.statusCode());
            }
        } catch (HttpTimeoutException exception) {
            throw new SmsProviderUnavailableException("SMS provider request timed out", exception);
        } catch (IOException exception) {
            throw new SmsProviderUnavailableException("Could not call SMS provider", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new SmsProviderUnavailableException("SMS provider call was interrupted", exception);
        }
    }
}
