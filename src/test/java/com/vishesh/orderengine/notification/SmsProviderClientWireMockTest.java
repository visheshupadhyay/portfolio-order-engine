package com.vishesh.orderengine.notification;

import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;

import org.junit.jupiter.api.Test;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
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
				providerBaseUri, new ObjectMapper(),
				Duration.ofSeconds(2),
				CircuitBreaker.ofDefaults("test"),
				Bulkhead.ofDefaults("test"));

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
				.withRequestBody(matchingJsonPath("$.message",
						equalTo("Order is paid orderID:order-101"))));
	}

	@Test
	public void throwsWhenProviderReturnsUnexpectedStatus(WireMockRuntimeInfo wireMockRuntimeInfo) {
		stubFor(post(urlEqualTo("/sms"))
				.withHeader("Idempotency-Key", equalTo("event-43"))
				.willReturn(aResponse().withStatus(500)));

		URI providerBaseUri = URI.create("http://localhost:" + wireMockRuntimeInfo.getHttpPort());
		SmsProviderClient client = new SmsProviderClient(
				HttpClient.newHttpClient(),
				providerBaseUri, new ObjectMapper(),
				Duration.ofSeconds(2),
				CircuitBreaker.ofDefaults("test"),
				Bulkhead.ofDefaults("test"));

		SmsProviderUnavailableException exception = assertThrows(SmsProviderUnavailableException.class, () -> client.send("OrderEngine",
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
				new ObjectMapper(),
				Duration.ofSeconds(2),
				CircuitBreaker.ofDefaults("test"),
				Bulkhead.ofDefaults("test"));

		client.send("OrderEngine", message, "event-44");

		verify(1, postRequestedFor(urlEqualTo("/sms"))
				.withHeader("Content-Type", equalTo("application/json"))
				.withHeader("Idempotency-Key", equalTo("event-44"))
				.withRequestBody(matchingJsonPath("$.senderName", equalTo("OrderEngine")))
				.withRequestBody(matchingJsonPath("$.message", equalTo(message))));
	}

	@Test
	public void throwsWhenProviderResponseExceedsRequestTimeout(WireMockRuntimeInfo wireMockRuntimeInfo)
			throws Exception {
		String message = "Order \"special\" is paid";
		stubFor(post(urlEqualTo("/sms"))
				.withHeader("Idempotency-Key", equalTo("event-42"))
				.willReturn(aResponse().withFixedDelay(1000).withStatus(202)));

		URI providerBaseUri = URI.create("http://localhost:" + wireMockRuntimeInfo.getHttpPort());
		SmsProviderClient client = new SmsProviderClient(HttpClient.newHttpClient(), providerBaseUri,
				new ObjectMapper(),
				Duration.ofMillis(100),
				CircuitBreaker.ofDefaults("test"),
				Bulkhead.ofDefaults("test"));

		SmsProviderUnavailableException exception = assertThrows(SmsProviderUnavailableException.class,
				() -> client.send("OrderEngine", message, "event-42"));

		assertEquals("SMS provider request timed out", exception.getMessage());
	}

	@Test
	public void opensCircuitBreakerAndStopsCallingFailingProvider(WireMockRuntimeInfo wireMockRuntimeInfo)
			throws Exception {

		stubFor(post(urlEqualTo("/sms"))
				.withHeader("Idempotency-Key", equalTo("event-42"))
				.willReturn(aResponse().withStatus(500)));

		// 1. Create a custom CircuitBreaker configuration
		CircuitBreakerConfig config = CircuitBreakerConfig.custom()
				.slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED) // Count-based sliding window
				.slidingWindowSize(2) // Window size: 2
				.minimumNumberOfCalls(2) // Minimum calls: 2
				.failureRateThreshold(50.0f) // Failure threshold: 50%
				.waitDurationInOpenState(Duration.ofMillis(100)) // Wait duration in OPEN: 100 milliseconds
				// One successful recovery call is enough to close this test breaker again.
				.permittedNumberOfCallsInHalfOpenState(1)
				.automaticTransitionFromOpenToHalfOpenEnabled(true)
				.build();

		// 2. Create a CircuitBreakerRegistry with the custom configuration
		CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(config);
		CircuitBreaker circuitBreaker = registry.circuitBreaker("sms-provider-test");

		URI providerBaseUri = URI.create("http://localhost:" + wireMockRuntimeInfo.getHttpPort());
		SmsProviderClient client = new SmsProviderClient(HttpClient.newHttpClient(), providerBaseUri,
				new ObjectMapper(),
				Duration.ofSeconds(2),
				circuitBreaker,
				Bulkhead.ofDefaults("test"));

		String message = "Order \"special\" is paid";
		assertThrows(RuntimeException.class, () -> client.send("Order-Engine", message, "event-42"));
		assertThrows(RuntimeException.class, () -> client.send("Order-Engine", message, "event-42"));

		assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());
		verify(2, postRequestedFor(urlEqualTo("/sms"))
				.withHeader("Content-Type", equalTo("application/json"))
				.withHeader("Idempotency-Key", equalTo("event-42"))
				.withRequestBody(matchingJsonPath("$.message", equalTo(message))));

		SmsProviderUnavailableException exception = assertThrows(SmsProviderUnavailableException.class, () -> client.send("Order-Engine", message, "event-42"));
		assertInstanceOf(CallNotPermittedException.class, exception.getCause());
		// The rejected third call must not create another HTTP request to WireMock.
		verify(2, postRequestedFor(urlEqualTo("/sms"))
				.withHeader("Content-Type", equalTo("application/json"))
				.withHeader("Idempotency-Key", equalTo("event-42"))
				.withRequestBody(matchingJsonPath("$.message", equalTo(message))));
		Thread.sleep(200);
		stubFor(post(urlEqualTo("/sms"))
				.withHeader("Idempotency-Key", equalTo("event-42"))
				.willReturn(aResponse().withStatus(202)));
		assertDoesNotThrow(() -> client.send("Order-Engine", message, "event-42"));

		
		verify(3, postRequestedFor(urlEqualTo("/sms"))
				.withHeader("Content-Type", equalTo("application/json"))
				.withHeader("Idempotency-Key", equalTo("event-42"))
				.withRequestBody(matchingJsonPath("$.message", equalTo(message))));
		assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.getState());
	}
}
