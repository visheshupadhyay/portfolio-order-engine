package com.vishesh.orderengine;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Verifies the real PostgreSQL payment path returns a freshly loaded PAID value,
// rather than the stale Java Order that existed before the direct SQL update.
// Payment also creates an outbox child row, so cleanup must remove that row first.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("postgres")
public class PostgresOrderPaymentApiTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private OrderRepository orderRepository;

    private String saveOrderId;

    private RequestPostProcessor writerCredentials() {
        return httpBasic("order-writer", "writer-password");
    }

    @AfterEach
    public void cleanUpDatabase() {
        if (saveOrderId != null) {
            jdbcTemplate.update("DELETE from outbox_events where order_id =?", saveOrderId);
            jdbcTemplate.update("DELETE from orders where id =?", saveOrderId);
        }
    }

    @Test
    public void returnsFreshPaidResponseFromPostgres() throws Exception {
        saveOrderId = "postgres-payment-test-" + UUID.randomUUID();
        Order order = new Order(saveOrderId);
        orderRepository.save(order);

        mockMvc.perform(post("/orders/" + saveOrderId + "/pay")
                .with(writerCredentials())
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(saveOrderId))
                .andExpect(jsonPath("$.status").value(OrderStatus.PAID.name()));
    }
}
