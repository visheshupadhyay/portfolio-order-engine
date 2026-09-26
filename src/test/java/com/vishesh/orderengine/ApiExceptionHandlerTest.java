package com.vishesh.orderengine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/* Direct unit test: verifies the handler's safe contract without starting MVC. */
public class ApiExceptionHandlerTest {

    @Test
    public void returnsServiceUnavailableForDatabaseResourceFailure() {
        ApiExceptionHandler apiExceptionHandler = new ApiExceptionHandler();
        // The real low-level message is intentionally different from the client response.
        DataAccessResourceFailureException exception = new DataAccessResourceFailureException("connection refused");
        ResponseEntity<ApiError> response = apiExceptionHandler.handleDataAccessResourceFailure(exception);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());

        ApiError body = response.getBody();
        assertNotNull(body);
        assertEquals(503, body.status());
        assertEquals("Database is temporarily unavailable", body.message());
    }
}
