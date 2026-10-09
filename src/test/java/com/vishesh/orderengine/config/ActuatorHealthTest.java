package com.vishesh.orderengine.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.vishesh.orderengine.security.JwtTokenService;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.hamcrest.Matchers.hasItem;

@SpringBootTest
@AutoConfigureMockMvc
public class ActuatorHealthTest {
	// Actuator registers these management endpoints through Spring Boot
	// auto-configuration; this test verifies the application exposes them safely.
	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private JwtTokenService jwtTokenService;

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
	public void reportsOverallAndDatabaseHealth() throws Exception {
		mockMvc.perform(get("/actuator/health"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"))
				.andExpect(jsonPath("$.components.db.status").value("UP"));
	}

	@Test
	public void exposesRegisteredMetricNamesLocally() throws Exception {
		mockMvc.perform(get("/actuator/metrics").with(adminToken()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.names").isArray())
				.andExpect(jsonPath("$.names")
						.value(hasItem("order.outbox.events.delivered")));
	}

	@Test
	public void exposesOutboxMetricsInPrometheusFormatLocally() throws Exception {
		mockMvc.perform(get("/actuator/prometheus").with(adminToken()))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
				.andExpect(content().string(containsString("order_outbox_events_delivered_total")))
				.andExpect(content().string(
						containsString("order_outbox_delivery_run_seconds_count")));
	}

	@Test
	public void reportsApplicationLiveness() throws Exception {
		mockMvc.perform(get("/actuator/health/liveness"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"));
	}

	@Test
	public void reportsApplicationAndDatabaseReadiness() throws Exception {
		mockMvc.perform(get("/actuator/health/readiness"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"))
				.andExpect(jsonPath("$.components.db.status").value("UP"));
	}
}
