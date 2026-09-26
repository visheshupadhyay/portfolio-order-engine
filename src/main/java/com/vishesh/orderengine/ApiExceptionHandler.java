package com.vishesh.orderengine;

import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

/*
 * Central MVC error translator: controllers and domain code can throw useful
 * exceptions, while clients always receive the same { status, message } JSON shape.
 */
@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> handleResponseStatusException(ResponseStatusException exception) {
        int code = exception.getStatusCode().value();
        String reason = exception.getReason();
        ApiError apiError = new ApiError(code, reason);
        return ResponseEntity.status(exception.getStatusCode()).body(apiError);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidationException(MethodArgumentNotValidException exception) {
        FieldError fieldError = exception.getBindingResult().getFieldError();
        String error = fieldError.getField() + " " + fieldError.getDefaultMessage();
        return ResponseEntity.badRequest().body(new ApiError(HttpStatus.BAD_REQUEST.value(), error));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiError> handleIllegalStateException(IllegalStateException exception) {
        ApiError apiError = new ApiError(HttpStatus.CONFLICT.value(), exception.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT.value()).body(apiError);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        String message = "Invalid " + exception.getName() + ": " + exception.getValue();
        return ResponseEntity.badRequest().body(new ApiError(HttpStatus.BAD_REQUEST.value(), message));
    }

    @ExceptionHandler(DataAccessResourceFailureException.class)
    public ResponseEntity<ApiError> handleDataAccessResourceFailure(DataAccessResourceFailureException exception) {
        // A connection/query-resource failure is transient from the client's point
        // of view. Do not return exception.getMessage(): it can reveal SQL/driver details.
        ApiError apiError = new ApiError(HttpStatus.SERVICE_UNAVAILABLE.value(),"Database is temporarily unavailable");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE.value()).body(apiError);
    }
}
