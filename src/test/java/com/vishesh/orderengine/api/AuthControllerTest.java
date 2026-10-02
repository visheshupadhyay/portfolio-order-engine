package com.vishesh.orderengine.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.util.Map;
import java.util.Set;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.vishesh.orderengine.security.JwtPrincipal;
import com.vishesh.orderengine.security.JwtTokenService;

/*
 * Exercises real credential validation, then parses the returned JWT to prove
 * the login endpoint returns a usable token rather than merely JSON text.
 */
@SpringBootTest
@AutoConfigureMockMvc
public class AuthControllerTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Test
    public void returnsBearerTokenForValidWriterCredentials() throws Exception {
        String requestBody = objectMapper
                .writeValueAsString(Map.of("username", "order-writer", "password", "writer-password"));
        MvcResult result = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty()).andReturn();

        String accessToken = objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
        JwtPrincipal jwtPrincipal = jwtTokenService.parse(accessToken);

        assertEquals("order-writer", jwtPrincipal.username());
        assertEquals(Set.of("ROLE_ORDER_WRITER"), jwtPrincipal.roles());
    }

    @Test
    public void returnsUnauthorizedForInvalidCredentials() throws Exception {
        String requestBody = objectMapper
                .writeValueAsString(Map.of("username", "order-writer", "password", "wrong-password"));
        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.path").value("/auth/login"))
                .andExpect(jsonPath("$.message").value("Invalid credentials"));
    }
}
