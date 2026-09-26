package com.vishesh.orderengine;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;

/*
 * End-to-end MVC contracts through the real security filter chain: readers
 * perform GETs, writers perform POSTs, and state-changing tests include CSRF.
 * A fresh context per test keeps the in-memory repository deterministic.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
public class OrderControllerTest {
    @Autowired
    private MockMvc mockMvc;

    private RequestPostProcessor readerCredentials() {
        return httpBasic("order-reader", "reader-password");
    }

    private RequestPostProcessor writerCredentials() {
        return httpBasic("order-writer", "writer-password");
    }

    @Test
    public void returnsOrderForKnownId() throws Exception {
        mockMvc.perform(get("/orders/order-101").with(readerCredentials()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("order-101"))
                .andExpect(jsonPath("$.status").value("CREATED"));

    }

    @Test
    public void returnsNotFoundForUnknownId() throws Exception {
        mockMvc.perform(get("/orders/order-999").with(readerCredentials()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Order not found: order-999"));
    }

    @Test
    public void createsOrderForValidRequest() throws Exception {
        mockMvc.perform(post("/orders").with(writerCredentials())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                            {"id":"order-103"}
                        """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/orders/order-103"))
                .andExpect(jsonPath("$.id").value("order-103"))
                .andExpect(jsonPath("$.status").value(OrderStatus.CREATED.name()));
    }

    @Test
    public void rejectsBlankOrderId() throws Exception {
        mockMvc.perform(post("/orders").with(writerCredentials())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                            {"id":" "}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("id must not be blank"));
    }

    @Test
    public void rejectsDuplicateOrderId() throws Exception {
        mockMvc.perform(post("/orders").with(writerCredentials())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                            {"id":"order-101"}
                        """)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Order already exists: order-101"));
    }

    @Test
    public void marksCreatedOrderAsPaid() throws Exception {
        mockMvc.perform(post("/orders/order-101/pay").with(writerCredentials())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("order-101"))
                .andExpect(jsonPath("$.status").value(OrderStatus.PAID.name()));
    }

    @Test
    public void returnsPaidForAlreadyPaidOrder() throws Exception {
        mockMvc.perform(post("/orders/order-102/pay").with(writerCredentials())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(OrderStatus.PAID.name()))
                .andExpect(jsonPath("$.id").value("order-102"));
    }

    @Test
    public void returnsAllImportedOrders() throws Exception {
        // The default profile returns the same page-response shape as JDBC and JPA profiles.
        mockMvc.perform(get("/orders").with(readerCredentials()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[*].id", containsInAnyOrder("order-101", "order-102")));
    }

    @Test
    public void returnsAllPaidImportedOrders() throws Exception {
        mockMvc.perform(get("/orders").with(readerCredentials()).param("status", "PAID"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value("order-102"))
                .andExpect(jsonPath("$.content[0].status").value(OrderStatus.PAID.name()));
    }

    @Test
    public void rejectsUnknownStatusFilter() throws Exception {
        mockMvc.perform(get("/orders").with(readerCredentials()).param("status", "UNKNOWN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Invalid status: UNKNOWN"));
    }

    @Test
    public void returnsFirstPageOfOrders() throws Exception {
        mockMvc.perform(get("/orders").with(readerCredentials())
                .param("page", "0")
                .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value("order-101"))
                .andExpect(jsonPath("$.content[0].status").value("CREATED"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    public void rejectsNegativePage() throws Exception {
        mockMvc.perform(get("/orders").with(readerCredentials()).param("page", "-1").param("size", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message")
                        .value("page must be greater than or equal to 0"));
    }

    @Test
    public void rejectsNonPositivePageSize() throws Exception {
        mockMvc.perform(get("/orders").with(readerCredentials()).param("page", "0").param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message")
                        .value("size must be greater than 0"));
    }

    @Test
    public void rejectsPageSizeAboveMaximum() throws Exception {
        mockMvc.perform(get("/orders")
                .param("page", "0")
                .param("size", "101")
                .with(readerCredentials()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("size must be less than or equal to 100"));
    }

    @Test
    public void acceptsMaximumPageSize() throws Exception {
        mockMvc.perform(get("/orders")
                .param("page", "0")
                .param("size", "100")
                .with(readerCredentials()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(100));
    }

    @Test
    public void returnsEmptyContentForPageBeyondAvailableOrders() throws Exception {
        mockMvc.perform(get("/orders")
                .with(readerCredentials())
                .param("page", "5")
                .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.page").value(5))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    public void returnsFirstCursorBatchOfOrders() throws Exception {
        mockMvc.perform(get("/orders/cursor")
                .with(readerCredentials())
                .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value("order-101"))
                .andExpect(jsonPath("$.content[0].status").value(OrderStatus.CREATED.name()))
                .andExpect(jsonPath("$.nextAfter").value("order-101"));
    }

    @Test
    public void returnsNextCursorBatchAfterProvidedId() throws Exception {
        mockMvc.perform(get("/orders/cursor")
                .with(readerCredentials())
                .param("size", "1")
                .param("after", "order-101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value("order-102"))
                .andExpect(jsonPath("$.content[0].status").value(OrderStatus.PAID.name()))
                .andExpect(jsonPath("$.nextAfter").value(nullValue()));
    }

    @Test
    public void returnsCursorBatchFilteredByStatus() throws Exception {
        mockMvc.perform(get("/orders/cursor")
                .with(readerCredentials())
                .param("size", "1")
                .param("status", OrderStatus.PAID.name()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value("order-102"))
                .andExpect(jsonPath("$.content[0].status").value(OrderStatus.PAID.name()))
                .andExpect(jsonPath("$.nextAfter").value(nullValue()));
    }

    @Test
    public void returnsCursorBatchFilteredByStatusAfterProvidedId() throws Exception {
        mockMvc.perform(get("/orders/cursor")
                .with(readerCredentials())
                .param("size", "1")
                .param("after", "order-101")
                .param("status", OrderStatus.PAID.name()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value("order-102"))
                .andExpect(jsonPath("$.content[0].status").value(OrderStatus.PAID.name()))
                .andExpect(jsonPath("$.nextAfter").value(nullValue()));
    }

    @Test
    public void guardTests() throws Exception {
        mockMvc.perform(get("/orders/cursor")
                .with(readerCredentials())
                .param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("size must be greater than 0"));
        mockMvc.perform(get("/orders/cursor")
                .with(readerCredentials())
                .param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("size must be less than or equal to 100"));
    }
}
