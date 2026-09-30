package com.vishesh.orderengine.api;

import com.vishesh.orderengine.integration.AbstractPostgresIntegrationTest;

import com.vishesh.orderengine.order.*;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
/* Secured API proof that cursor continuation reaches the JDBC/PostgreSQL implementation. */
public class PostgresOrderCursorApiTest extends AbstractPostgresIntegrationTest {
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
        // The four fixtures let this test verify first batch, continuation, filter, and filtered continuation.
        assertInstanceOf(JdbcOrderRepository.class, orderRepository);
         jdbcTemplate.update("DELETE from outbox_events");
        jdbcTemplate.update("DELETE from order_items");
        jdbcTemplate.update("DELETE from orders");
        String savedOrderIdA = "postgres-cursor-a";
        String savedOrderIdB = "postgres-cursor-b";
        String savedOrderIdC = "postgres-cursor-c";
        String savedOrderIdD = "postgres-cursor-d";
        Order orderA = new Order(savedOrderIdA, OrderStatus.CREATED);
        Order orderB = new Order(savedOrderIdB, OrderStatus.PAID);
        Order orderC = new Order(savedOrderIdC, OrderStatus.CREATED);
        Order orderD = new Order(savedOrderIdD, OrderStatus.PAID);
        orderRepository.save(orderA);
        orderRepository.save(orderB);
        orderRepository.save(orderC);
        orderRepository.save(orderD);

        mockMvc.perform(get("/orders/cursor")
                .param("size", "2")
                .with(readerCredentials()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].id").value(savedOrderIdA))
                .andExpect(jsonPath("$.content[1].id").value(savedOrderIdB))
                .andExpect(jsonPath("$.nextAfter").value(savedOrderIdB));
        
        mockMvc.perform(get("/orders/cursor")
                .param("size", "2")
                .param("after", savedOrderIdB)
                .with(readerCredentials()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].id").value(savedOrderIdC))
                .andExpect(jsonPath("$.content[1].id").value(savedOrderIdD))
                .andExpect(jsonPath("$.nextAfter").value(nullValue()));
        
        mockMvc.perform(get("/orders/cursor")
                .param("size", "1")
                .param("status", OrderStatus.CREATED.name())
                .with(readerCredentials()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(savedOrderIdA))
                .andExpect(jsonPath("$.nextAfter").value(savedOrderIdA));
        
        mockMvc.perform(get("/orders/cursor")
                .param("size", "1")
                .param("status", OrderStatus.CREATED.name())
                .param("after", savedOrderIdA)
                .with(readerCredentials()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(savedOrderIdC))
                .andExpect(jsonPath("$.nextAfter").value(nullValue()));
    }
}
