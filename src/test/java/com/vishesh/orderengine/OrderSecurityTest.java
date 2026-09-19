package com.vishesh.orderengine;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/*
 * Focused security matrix: unauthenticated requests get 401; authenticated
 * requests still need CSRF for writes and the correct role for each route.
 */
@SpringBootTest
@AutoConfigureMockMvc
public class OrderSecurityTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    public void rejectsUnauthenticatedOrderRequest() throws Exception {
        mockMvc.perform(get("/orders/order-101"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    public void allowsAuthenticatedOrderRequest() throws Exception {
        mockMvc.perform(get("/orders/order-101")
                .with(httpBasic("order-reader", "reader-password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("order-101"));

    }

    @Test
    public void rejectsAuthenticatedPostWithoutCsrfToken() throws Exception {
        mockMvc.perform(post("/orders")
                .with(httpBasic("order-reader", "reader-password"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"id":"order-103"}
                        """))
                .andExpect(status().isForbidden());
    }

    @Test
    public void rejectsReaderPostEvenWithCsrfToken() throws Exception {
        mockMvc.perform(post("/orders")
                .with(httpBasic("order-reader", "reader-password"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"id":"order-103"}
                        """))
                .andExpect(status().isForbidden());
    }

    @Test
    public void allowsWriterPostWithCsrfToken() throws Exception {
        mockMvc.perform(post("/orders")
                .with(httpBasic("order-writer", "writer-password"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"id":"order-103"}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("order-103"))
                .andExpect(jsonPath("$.status").value("CREATED"));
    }

    @Test
    public void rejectsWriterPostWithoutCsrfToken() throws Exception {
        mockMvc.perform(post("/orders")
                .with(httpBasic("order-writer", "writer-password"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"id":"order-103"}
                        """))
                .andExpect(status().isForbidden());

    }
}
