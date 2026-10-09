package com.vishesh.orderengine.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;

import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
        "resilience4j.ratelimiter.instances.loginAttempts.limit-for-period=1",
        "resilience4j.ratelimiter.instances.loginAttempts.limit-refresh-period=PT1H",
        "resilience4j.ratelimiter.instances.loginAttempts.timeout-duration=PT0S"
})
@AutoConfigureMockMvc
public class LoginRateLimitSecurityIntegrationTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired 
    private ObjectMapper objectMapper;

    @Test
    public void rejectsSecondLoginAttemptThroughSecurityFilterChain() throws Exception {
        String requestBody = objectMapper.writeValueAsString(Map.of("username","order-reader","password","wrong-password"));
        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)).andExpect(status().isUnauthorized());

        requestBody = objectMapper.writeValueAsString(Map.of("username","order-reader","password","reader-password"));
        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("Too Many Requests"))
                .andExpect(jsonPath("$.message").value("Rate limit exceeded. Please try again later."));
    }
}
