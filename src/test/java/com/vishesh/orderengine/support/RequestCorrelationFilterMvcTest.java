package com.vishesh.orderengine.support;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
public class RequestCorrelationFilterMvcTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    public void returnsClientProvidedRequestId() throws Exception {
        mockMvc.perform(get("/actuator/health")
                .header("X-Request-Id", "client-123"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", "client-123"));
    }

    @Test
    public void generatesRequestIdWhenClientDoesNotProvideOne() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-Id"));
    }

    @Test
    public void generatesNonBlankRequestIdWhenClientProvidesBlankId() throws Exception {
        String requestId = mockMvc.perform(get("/actuator/health")
                .header("X-Request-Id", ""))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-Id")).andReturn().getResponse().getHeader("X-Request-Id");

        assertNotNull(requestId);
        assertFalse(requestId.isBlank());
    }

    @Test
    public void clearsRequestIdFromMdcAfterRequestCompletes() throws Exception {
        mockMvc.perform(get("/actuator/health")
                .header("X-Request-Id", "client-123"))
                .andExpect(status().isOk());

        assertNull(MDC.get("requestId"));
    }
}
