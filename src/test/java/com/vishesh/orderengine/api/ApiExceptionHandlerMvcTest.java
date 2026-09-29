package com.vishesh.orderengine.api;

import com.vishesh.orderengine.order.*;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

/* MVC-level companion to ApiExceptionHandlerTest: proves Spring routes the exception to advice. */
/* HTTP-level safety checks: status and JSON shape must stay stable while SQL and
 * framework exception messages remain private. */
public class ApiExceptionHandlerMvcTest {
    private MockMvc mockMvc;

    @BeforeEach
    public void setupMockMvc() {
        mockMvc = MockMvcBuilders.standaloneSetup(new DatabaseFailureTestController())
                .setControllerAdvice(new ApiExceptionHandler()).build();

    }

    @RestController
    static class DatabaseFailureTestController {
        // Test-only endpoint: it simulates a repository/database resource outage.
        @GetMapping("/test/database-failure")
        void throwDatabaseFailure() {
            throw new DataAccessResourceFailureException("connection refused");
        }

        @PostMapping("/test/empty_order")
        void acceptOrder(@Valid @RequestBody CreateOrderRequest request) {

        }

        @GetMapping ("/test/database-query-failure")
        void databaseFailue() {
            throw new DataIntegrityViolationException("Sensitive SQL details: SELECT * FROM private_table");
        }
    }

    @Test
    public void returnsSafeDatabaseErrorThroughMvc() throws Exception {
        mockMvc.perform(get("/test/database-failure"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.path").value("/test/database-failure"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(jsonPath("$.message")
                        .value("Database is temporarily unavailable"));
    }

    @Test
    public void blankIdValidationCase() throws Exception {
        // mockMvc.perform(get("/test/database-failure")).andDo(print());
        mockMvc.perform(post("/test/empty_order")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                            {"id":" "}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.path").value("/test/empty_order"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.fieldErrors.id").value("must not be blank"));
    }

    @Test
    public void databaseFailureTest() throws Exception {
        mockMvc.perform(get("/test/database-query-failure"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.path").value("/test/database-query-failure"))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.message").value("A database error occurred"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());
    }
}
