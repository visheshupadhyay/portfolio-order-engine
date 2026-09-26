package com.vishesh.orderengine;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/* MVC-level companion to ApiExceptionHandlerTest: proves Spring routes the exception to advice. */
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
    }

    @Test
    public void returnsSafeDatabaseErrorThroughMvc() throws Exception {
        mockMvc.perform(get("/test/database-failure"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.message")
                        .value("Database is temporarily unavailable"));
    }
}
