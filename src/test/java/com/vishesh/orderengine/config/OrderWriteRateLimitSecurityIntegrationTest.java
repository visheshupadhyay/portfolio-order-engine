package com.vishesh.orderengine.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.vishesh.orderengine.security.JwtTokenService;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
        "resilience4j.ratelimiter.instances.orderWrites.limit-for-period=1",
        "resilience4j.ratelimiter.instances.orderWrites.limit-refresh-period=PT1H",
        "resilience4j.ratelimiter.instances.orderWrites.timeout-duration=PT0S"
})
@AutoConfigureMockMvc
public class OrderWriteRateLimitSecurityIntegrationTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtTokenService jwtTokenService;
    @Autowired
    private ObjectMapper objectMapper;

    private RequestPostProcessor writerToken() {
        String token = jwtTokenService.issue(
                "order-writer",
                Set.of("ROLE_ORDER_WRITER"));

        return request -> {
            request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
            return request;
        };
    }

    @Test
    public void rejectsSecondWriterOrderRequestThroughSecurityFilterChain() throws Exception {
        String firstOrder = UUID.randomUUID().toString();
        String requestBody = objectMapper.writeValueAsString(Map.of("id", firstOrder));
        mockMvc.perform(post("/orders")
                .with(writerToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)).andExpect(status().isCreated());

        String secondOrder = UUID.randomUUID().toString();
        requestBody = objectMapper.writeValueAsString(Map.of("id", secondOrder));
        mockMvc.perform(post("/orders")
                .with(writerToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("Too Many Requests"))
                .andExpect(jsonPath("$.message").value("Rate limit exceeded. Please try again later."));
        ;
    }
}
