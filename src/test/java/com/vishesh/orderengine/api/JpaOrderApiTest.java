package com.vishesh.orderengine.api;

import com.vishesh.orderengine.integration.AbstractPostgresIntegrationTest;

import com.vishesh.orderengine.order.*;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.util.ArrayList;
import java.util.List;
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
import org.springframework.transaction.annotation.Transactional;

/*
 * Full public-path proof for the jpa profile: security, controller, service,
 * profile-selected adapter, Hibernate, and PostgreSQL must all agree. This test
 * deliberately has no test transaction, so @AfterEach removes its committed row.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("jpa")
public class JpaOrderApiTest extends AbstractPostgresIntegrationTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private OrderRepository orderRepository;
    private List<String> savedOrderIds = new ArrayList<>();

    private RequestPostProcessor readerCredentials() {
        return httpBasic("order-reader", "reader-password");
    }

    private RequestPostProcessor writerCredentials() {
        return httpBasic("order-writer", "writer-password");
    }

    @AfterEach
    public void cleanUpDatabase() {
        if (!savedOrderIds.isEmpty()) {
            // Cleanup is intentionally direct SQL; production behavior is tested above.
            for (String savedOrderId : savedOrderIds) {
                jdbcTemplate.update("DELETE FROM order_items WHERE order_id = ?", savedOrderId);
                jdbcTemplate.update("DELETE FROM outbox_events WHERE order_id = ?", savedOrderId);
                jdbcTemplate.update("DELETE FROM orders WHERE id = ?", savedOrderId);
            }
        }
    }

    @Test
    public void createsPaysAndReadsOrderThroughJpaProfile() throws Exception {
        assertInstanceOf(JpaOrderRepository.class, orderRepository);
        String savedOrderId = "jpa-order-api-test" + UUID.randomUUID();
        savedOrderIds.add(savedOrderId);
        String requestBody = "{\"id\":\"" + savedOrderId + "\"}";
        // The first request reaches the JPA adapter and persists a CREATED row.
        mockMvc.perform(post("/orders")
                .with(writerCredentials())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(OrderStatus.CREATED.name()))
                .andExpect(jsonPath("$.id").value(savedOrderId));

        // The same public request must expose the adapter's atomic duplicate result as
        // 409.
        mockMvc.perform(post("/orders")
                .with(writerCredentials())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)).andExpect(status().isConflict());

        // Payment uses the conditional state transition and returns the freshly
        // reloaded PAID value.
        mockMvc.perform(post("/orders/" + savedOrderId + "/pay")
                .with(writerCredentials())
                .with(csrf())).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(OrderStatus.PAID.name()))
                .andExpect(jsonPath("$.id").value(savedOrderId));

        mockMvc.perform(get("/orders/" + savedOrderId)
                .with(readerCredentials()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(OrderStatus.PAID.name()));
    }

    @Test
    @Transactional
    public void returnsPagedFilteredOrdersThroughJpaApi() throws Exception {
        assertInstanceOf(JpaOrderRepository.class, orderRepository);
        // One rollback-only fixture supports both unfiltered and status-filtered page assertions.
        jdbcTemplate.update("DELETE FROM order_items");
        jdbcTemplate.update("DELETE FROM outbox_events");
        jdbcTemplate.update("DELETE FROM orders");
        String savedOrderIdA = "jpa-order-test-a";
        String savedOrderIdB = "jpa-order-test-b";
        String savedOrderIdC = "jpa-order-test-c";

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
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    @Transactional
    public void returnsCursorBatchesThroughJpaApi() throws Exception {
        assertInstanceOf(JpaOrderRepository.class, orderRepository);
        // Cursor assertions prove outgoing nextAfter can be used as the next incoming after value.
        jdbcTemplate.update("Delete from order_items");
        jdbcTemplate.update("Delete from outbox_events");
        jdbcTemplate.update("Delete from orders");
        String savedOrderIdA = "jpa-cursor-a";
        String savedOrderIdB = "jpa-cursor-b";
        String savedOrderIdC = "jpa-cursor-c";
        String savedOrderIdD = "jpa-cursor-d";
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
                .andExpect(jsonPath("$.content[0].status").value(OrderStatus.CREATED.name()))
                .andExpect(jsonPath("$.content[1].id").value(savedOrderIdB))
                .andExpect(jsonPath("$.content[1].status").value(OrderStatus.PAID.name()))
                .andExpect(jsonPath("$.nextAfter").value(savedOrderIdB));

        mockMvc.perform(get("/orders/cursor")
                .param("size", "2")
                .param("after", savedOrderIdB)
                .with(readerCredentials()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].id").value(savedOrderIdC))
                .andExpect(jsonPath("$.content[0].status").value(OrderStatus.CREATED.name()))
                .andExpect(jsonPath("$.content[1].id").value(savedOrderIdD))
                .andExpect(jsonPath("$.content[1].status").value(OrderStatus.PAID.name()))
                .andExpect(jsonPath("$.nextAfter").value(nullValue()));

        mockMvc.perform(get("/orders/cursor")
                .param("size", "1")
                .param("status", OrderStatus.CREATED.name())
                .with(readerCredentials()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(savedOrderIdA))
                .andExpect(jsonPath("$.content[0].status").value(OrderStatus.CREATED.name()))
                .andExpect(jsonPath("$.nextAfter").value(savedOrderIdA));

    }
}
