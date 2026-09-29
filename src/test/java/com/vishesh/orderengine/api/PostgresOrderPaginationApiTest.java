package com.vishesh.orderengine.api;

import com.vishesh.orderengine.order.*;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("postgres")
@Transactional
/* Secured API proof that GET /orders reaches handwritten JDBC pagination SQL. */
public class PostgresOrderPaginationApiTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private OrderRepository orderRepository;

    private RequestPostProcessor readerCredentials() {
        return httpBasic("order-reader", "reader-password");
    }

    private RequestPostProcessor writerCredentials() {
        return httpBasic("order-writer", "writer-password");
    }

    @Test
    public void returnsPagedFilteredOrdersThroughPostgresApi() throws Exception {
        // The profile assertion prevents this API test from silently exercising another adapter.
        assertInstanceOf(JdbcOrderRepository.class, orderRepository);
        jdbcTemplate.update("DELETE from order_items");
        jdbcTemplate.update("DELETE from outbox_events");
        jdbcTemplate.update("DELETE from orders");
        String savedOrderIdA = "postgres-api-page-a" + UUID.randomUUID();
        String savedOrderIdB = "postgres-api-page-b" + UUID.randomUUID();
        String savedOrderIdC = "postgres-api-page-c" + UUID.randomUUID();
        Order orderA = new Order(savedOrderIdA, OrderStatus.CREATED);
        Order orderB = new Order(savedOrderIdB, OrderStatus.PAID);
        Order orderC = new Order(savedOrderIdC, OrderStatus.CREATED);
        orderRepository.save(orderA);
        orderRepository.save(orderB);
        orderRepository.save(orderC);

        mockMvc.perform(get("/orders")
                .with(readerCredentials())
                .param("page", "0")
                .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].id").value(savedOrderIdA))
                .andExpect(jsonPath("$.content[1].id").value(savedOrderIdB))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3));

        mockMvc.perform(get("/orders")
                .with(readerCredentials())
                .param("page", "0")
                .param("size", "2")
                .param("status", OrderStatus.CREATED.name()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].id").value(savedOrderIdA))
                .andExpect(jsonPath("$.content[0].status").value(OrderStatus.CREATED.name()))
                .andExpect(jsonPath("$.content[1].id").value(savedOrderIdC))
                .andExpect(jsonPath("$.content[1].status").value(OrderStatus.CREATED.name()))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(2));
    }
}
