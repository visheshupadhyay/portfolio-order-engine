package com.vishesh.orderengine.notification;

import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.net.http.HttpClient;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;

import org.junit.jupiter.api.Test;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;

import tools.jackson.databind.ObjectMapper;

@WireMockTest
public class SmsProviderClientWireMockTest {
    @Test
    void sendsSmsRequestToProvider(WireMockRuntimeInfo wireMockRuntimeInfo) {
        // 1. Tell WireMock which request it should accept and how to respond.
        stubFor(post(urlEqualTo("/sms"))
                .withHeader("Idempotency-Key", equalTo("event-42"))
                .willReturn(aResponse().withStatus(202)));

        URI providerBaseUri = URI.create("http://localhost:" + wireMockRuntimeInfo.getHttpPort());
        // 2. Create SmsProviderClient using WireMock's temporary URL.
        SmsProviderClient client = new SmsProviderClient(
                HttpClient.newHttpClient(),
                providerBaseUri, new ObjectMapper());

        // 3. Call client.send(...).
        client.send(
                "OrderEngine",
                "Order is paid orderID:order-101",
                "event-42");
        // 4. Verify WireMock received the expected request.
        verify(1, postRequestedFor(urlEqualTo("/sms"))
                .withHeader("Content-Type", equalTo("application/json"))
                .withHeader("Idempotency-Key", equalTo("event-42"))
                .withRequestBody(matchingJsonPath("$.senderName", equalTo("OrderEngine")))
                .withRequestBody(matchingJsonPath("$.message", equalTo("Order is paid orderID:order-101"))));
    }

    @Test
    public void throwsWhenProviderReturnsUnexpectedStatus(WireMockRuntimeInfo wireMockRuntimeInfo) {
        stubFor(post(urlEqualTo("/sms"))
                .withHeader("Idempotency-Key", equalTo("event-43"))
                .willReturn(aResponse().withStatus(500)));

        URI providerBaseUri = URI.create("http://localhost:" + wireMockRuntimeInfo.getHttpPort());
        SmsProviderClient client = new SmsProviderClient(
                HttpClient.newHttpClient(),
                providerBaseUri, new ObjectMapper());

        RuntimeException exception = assertThrows(RuntimeException.class, () -> client.send("OrderEngine",
                "Order is paid orderID:order-102",
                "event-43"));

        assertEquals("SMS provider returned unexpected status: 500", exception.getMessage());
        verify(1, postRequestedFor(urlEqualTo("/sms"))
                .withHeader("Content-Type", equalTo("application/json"))
                .withHeader("Idempotency-Key", equalTo("event-43")));
    }

    @Test
    public void serializesQuotesInSmsMessage(WireMockRuntimeInfo wireMockRuntimeInfo) {
        String message = "Order \"special\" is paid";
        stubFor(post(urlEqualTo("/sms"))
                .withHeader("Idempotency-Key", equalTo("event-44"))
                .willReturn(aResponse().withStatus(202)));

        URI providerBaseUri = URI.create("http://localhost:" + wireMockRuntimeInfo.getHttpPort());
        SmsProviderClient client = new SmsProviderClient(HttpClient.newHttpClient(), providerBaseUri,
                new ObjectMapper());

        client.send("OrderEngine", message, "event-44");

        verify(1, postRequestedFor(urlEqualTo("/sms"))
                .withHeader("Content-Type", equalTo("application/json"))
                .withHeader("Idempotency-Key", equalTo("event-44"))
                .withRequestBody(matchingJsonPath("$.senderName", equalTo("OrderEngine")))
                .withRequestBody(matchingJsonPath("$.message", equalTo(message))));
    }
}
