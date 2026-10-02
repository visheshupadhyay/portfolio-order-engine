package com.vishesh.orderengine.notification;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import tools.jackson.databind.ObjectMapper;

/*
 * Outbound HTTP adapter for the SMS provider. WireMock tests protect this
 * request contract without contacting a real provider during Maven tests.
 */
public class SmsProviderClient {
    private final HttpClient httpClient;
    private final URI providerBaseUri;
    private final ObjectMapper objectMapper;

    public SmsProviderClient(HttpClient httpClient, URI providerBaseUri, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.providerBaseUri = providerBaseUri;
        this.objectMapper = objectMapper;
    }

    public void send(String senderName, String message, String idempotencyKey) {
        try {
            String requestBody = objectMapper.writeValueAsString(Map.of("senderName", senderName, "message", message));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(providerBaseUri.resolve("/sms"))
                    .header("Content-Type", "application/json")
                    .header("Idempotency-Key", idempotencyKey)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();
    
            HttpResponse<Void> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.discarding());

            // A provider acknowledgement is the only successful delivery handoff.
            if (response.statusCode() != 202) {
                throw new RuntimeException(
                        "SMS provider returned unexpected status: " + response.statusCode());
            }
        } catch (IOException exception) {
            throw new RuntimeException("Could not call SMS provider", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("SMS provider call was interrupted", exception);
        }
    }
}
