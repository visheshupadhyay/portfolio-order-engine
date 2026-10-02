package com.vishesh.orderengine.api;

import org.springframework.http.HttpHeaders;
import com.vishesh.orderengine.integration.AbstractPostgresIntegrationTest;

import com.vishesh.orderengine.order.*;
import com.vishesh.orderengine.security.JwtTokenService;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

// Full path test: HTTP security, controller conflict handling, JDBC, and the
// PostgreSQL unique key must agree on duplicate creation behavior.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("postgres")
public class PostgresOrderCreationApiTest extends AbstractPostgresIntegrationTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String saveOrderId;

    @Autowired
    private JwtTokenService jwtTokenService;

    private RequestPostProcessor writerToken() {
        String token = jwtTokenService.issue(
                "order-writer",
                Set.of("ROLE_ORDER_WRITER"));

        return request -> {
            request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
            return request;
        };
    }

    @AfterEach
    public void cleanUpDatabase() {
        if (saveOrderId != null) {
            jdbcTemplate.update("DELETE from orders where id =?", saveOrderId);
        }
    }

    @Test
    public void rejectsDuplicateCreationThroughPostgresApi() throws Exception {
        saveOrderId = "postgres-order-creation-test-" + UUID.randomUUID();
        String requestBody = "{\"id\":\"" + saveOrderId + "\"}";
        // The first request wins the atomic insert; the identical second request must
        // surface the repository's false result as the public 409 contract.
        mockMvc.perform(post("/orders").with(writerToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)).andExpect(status().isCreated());

        mockMvc.perform(post("/orders").with(writerToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)).andExpect(status().isConflict());

    }
}
