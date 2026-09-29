package com.vishesh.orderengine.api;

import com.vishesh.orderengine.order.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

/* Direct unit test: verifies the handler's safe contract without starting MVC. */
/* Focused unit checks for the Java handler result; MVC serialization is covered
 * separately by ApiExceptionHandlerMvcTest. */
public class ApiExceptionHandlerTest {

    @Test
    public void returnsServiceUnavailableForDatabaseResourceFailure() {
        MockHttpServletRequest mockHttpServletRequest = new MockHttpServletRequest();
        mockHttpServletRequest.setRequestURI("/test/database-failure");
        ApiExceptionHandler apiExceptionHandler = new ApiExceptionHandler();
        // The real low-level message is intentionally different from the client response.
        DataAccessResourceFailureException exception = new DataAccessResourceFailureException("connection refused");
        ResponseEntity<ApiError> response = apiExceptionHandler.handleDataAccessResourceFailure(exception,mockHttpServletRequest);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());

        ApiError body = response.getBody();
        assertNotNull(body);
        assertEquals(503, body.status());
        assertNotNull(body.timestamp());
        assertEquals(Map.of(),body.fieldErrors());
        assertEquals("/test/database-failure", body.path());
        assertEquals("Database is temporarily unavailable", body.message());
    }
}
