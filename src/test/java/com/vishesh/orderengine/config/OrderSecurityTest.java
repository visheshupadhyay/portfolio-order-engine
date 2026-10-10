package com.vishesh.orderengine.config;

import com.vishesh.orderengine.order.*;
import com.vishesh.orderengine.security.JwtProperties;
import com.vishesh.orderengine.security.JwtTokenService;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Set;
import org.springframework.http.HttpHeaders;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/*
 * Focused security matrix: unauthenticated requests get 401, while valid
 * Bearer tokens still need the correct role for each protected route.
 */
@SpringBootTest
@AutoConfigureMockMvc
public class OrderSecurityTest {
	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private JwtTokenService jwtTokenService;
	@Autowired
	private JwtProperties jwtProperties;

	private RequestPostProcessor readerToken() {
		String token = jwtTokenService.issue(
				"order-reader",
				Set.of("ROLE_ORDER_READER"));

		return request -> {
			request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
			return request;
		};
	}

	private RequestPostProcessor writerToken() {
		String token = jwtTokenService.issue(
				"order-writer",
				Set.of("ROLE_ORDER_WRITER"));

		return request -> {
			request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
			return request;
		};
	}

	private RequestPostProcessor adminToken() {
		String token = jwtTokenService.issue(
				"order-admin",
				Set.of("ROLE_ORDER_ADMIN"));

		return request -> {
			request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
			return request;
		};
	}

	@Test
	public void rejectsUnauthenticatedOrderRequest() throws Exception {
		mockMvc.perform(get("/orders/order-101"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	public void allowsAuthenticatedOrderRequest() throws Exception {
		mockMvc.perform(get("/orders/order-101")
				.with(readerToken()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value("order-101"));

	}

	@Test
	public void rejectsReaderPostWithValidBearerToken() throws Exception {
		mockMvc.perform(post("/orders")
				.with(readerToken())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"id":"order-103"}
						"""))
				.andExpect(status().isForbidden());
	}

	@Test
	public void allowsWriterPostWithBearerToken() throws Exception {
		mockMvc.perform(post("/orders")
				.with(writerToken())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"id":"order-103"}
						"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").value("order-103"))
				.andExpect(jsonPath("$.status").value("CREATED"));
	}

	@Test
	public void rejectsMalformedBearerToken() throws Exception {
		mockMvc.perform(get("/orders/order-101")
				.header(HttpHeaders.AUTHORIZATION, "Bearer not-a-real-jwt"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	public void rejectsExpiredBearerToken() throws Exception {
		Instant pastTime = Instant.now().minus(Duration.ofHours(1));
		Clock clock = Clock.fixed(pastTime, ZoneId.of("UTC"));

		JwtTokenService tokenService = new JwtTokenService(jwtProperties.issuer(),
				jwtProperties.base64Secret(),
				jwtProperties.accessTokenTtl(),
				clock);

		String expiredToken = tokenService.issue("order-reader",
				Set.of("ROLE_ORDER_READER"));

		mockMvc.perform(get("/orders/order-101")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken))
				.andExpect(status().isUnauthorized());
	}

	@Test
	public void allowsHealthEndpointWithoutCredentials() throws Exception {
		mockMvc.perform(get("/actuator/health"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"));
	}

	@Test
	public void rejectsMetricsEndpointWithReaderCredentials() throws Exception {
		mockMvc.perform(get("/actuator/metrics").with(readerToken()))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/actuator/metrics"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	public void rejectsUnknownPath() throws Exception {
		mockMvc.perform(get("/internal/not-configured").with(readerToken()))
				.andExpect(status().isForbidden());
	}

	@Test
	public void allowsPrometheusEndpointWithoutCredentials() throws Exception {
		mockMvc.perform(get("/actuator/prometheus"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("jvm_memory_used_bytes")));
	}

	@Test
	public void allowsMetricsEndpointWithAdminCredentials() throws Exception {
		mockMvc.perform(get("/actuator/metrics")
				.with(adminToken()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.names").isArray());
	}

	@Test
	public void exportsHttpRequestLatencyBuckets() throws Exception {
		mockMvc.perform(get("/actuator/health"))
				.andExpect(status().isOk());

		mockMvc.perform(get("/actuator/prometheus"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("http_server_requests_seconds_bucket")));

	}
}
