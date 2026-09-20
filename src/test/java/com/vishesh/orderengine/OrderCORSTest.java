package com.vishesh.orderengine;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

/*
 * A CORS preflight is an OPTIONS request sent by a browser before a cross-origin
 * request. These tests protect both sides of the policy: our frontend is allowed
 * and an untrusted origin is rejected before it can call the order API.
 */
@SpringBootTest
@AutoConfigureMockMvc
public class OrderCORSTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    public void allowsOrderApiPreflightFromLocalFrontend() throws Exception {
        mockMvc.perform(options("/orders")
                .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        "http://localhost:3000"));
    }

    @Test
    public void rejectsPreflightFromUnknownOrigin() throws Exception {
        mockMvc.perform(options("/orders")
                .header(HttpHeaders.ORIGIN, "http://evil.example")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden());
    }
}
