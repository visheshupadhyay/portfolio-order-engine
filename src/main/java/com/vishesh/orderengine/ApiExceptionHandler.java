package com.vishesh.orderengine;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpServletRequest;

/*
 * Central MVC error translator: controllers and domain code can throw useful
 * exceptions, while clients always receive the same safe JSON shape. Request
 * paths help clients correlate an error without leaking exception internals.
 */
@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> handleResponseStatusException(ResponseStatusException exception,
            HttpServletRequest servletRequest) {

        int status = exception.getStatusCode().value();
        String message = exception.getReason();
        LocalDateTime timestamp = LocalDateTime.now();
        String path = servletRequest.getRequestURI();

        ApiError apiError = new ApiError(status, message, timestamp, path, Map.of());
        return ResponseEntity.status(exception.getStatusCode()).body(apiError);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidationException(MethodArgumentNotValidException exception,
            HttpServletRequest servletRequest) {

        int status = HttpStatus.BAD_REQUEST.value();
        FieldError fieldError = exception.getBindingResult().getFieldError();
        String message = fieldError.getField() + " " + fieldError.getDefaultMessage();
        LocalDateTime timestamp = LocalDateTime.now();
        String path = servletRequest.getRequestURI();
        // Keep every invalid field, rather than making clients correct one field
        // and resubmit repeatedly. Duplicate messages for the same field merge.
        Map<String, String> fieldErrors = exception.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        FieldError::getDefaultMessage,
                        (firstMessage, nextMessage) -> firstMessage + "; " + nextMessage));

        ApiError apiError = new ApiError(status, message, timestamp, path, fieldErrors);
        return ResponseEntity.badRequest().body(apiError);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiError> handleIllegalStateException(IllegalStateException exception,
            HttpServletRequest servletRequest) {
        int status = HttpStatus.CONFLICT.value();
        String message = exception.getMessage();
        LocalDateTime timestamp = LocalDateTime.now();
        String path = servletRequest.getRequestURI();
        ApiError apiError = new ApiError(status, message, timestamp, path, Map.of());
        return ResponseEntity.status(HttpStatus.CONFLICT.value()).body(apiError);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException exception,
            HttpServletRequest servletRequest) {

        int status = HttpStatus.BAD_REQUEST.value();
        String message = "Invalid " + exception.getName() + ": " + exception.getValue();
        LocalDateTime timestamp = LocalDateTime.now();
        String path = servletRequest.getRequestURI();
        ApiError apiError = new ApiError(status, message, timestamp, path, Map.of());
        return ResponseEntity.badRequest().body(apiError);
    }

    @ExceptionHandler(DataAccessResourceFailureException.class)
    public ResponseEntity<ApiError> handleDataAccessResourceFailure(DataAccessResourceFailureException exception,
            HttpServletRequest servletRequest) {
        // A connection/query-resource failure is transient from the client's point
        // of view. Do not return exception.getMessage(): it can reveal SQL/driver
        // details.
        int status = HttpStatus.SERVICE_UNAVAILABLE.value();
        String message = "Database is temporarily unavailable";
        LocalDateTime timestamp = LocalDateTime.now();
        String path = servletRequest.getRequestURI();
        ApiError apiError = new ApiError(status, message, timestamp, path, Map.of());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE.value()).body(apiError);
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiError> handleDataAccessFailure(DataAccessException exception,
            HttpServletRequest servletRequest) {
        // Other database failures are not automatically safe to retry, so return
        // 500 rather than 503—but still hide SQL/driver details from the client.
        int status = HttpStatus.INTERNAL_SERVER_ERROR.value();
        String message = "A database error occurred";
        LocalDateTime timestamp = LocalDateTime.now();
        String path = servletRequest.getRequestURI();
        ApiError apiError = new ApiError(status, message, timestamp, path, Map.of());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR.value()).body(apiError);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableRequestBody(HttpMessageNotReadableException exception,
            HttpServletRequest servletRequest) {
        int status = HttpStatus.BAD_REQUEST.value();
        String message = "Invalid JSON";
        LocalDateTime timestamp = LocalDateTime.now();
        String path = servletRequest.getRequestURI();
        ApiError apiError = new ApiError(status, message, timestamp, path, Map.of());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST.value()).body(apiError);
    }
}
