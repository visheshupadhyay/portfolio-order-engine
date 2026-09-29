package com.vishesh.orderengine.api;

import java.time.LocalDateTime;
import java.util.Map;

/*
 * Stable JSON error contract returned by ApiExceptionHandler instead of
 * framework-specific errors. fieldErrors is always an object: empty for a
 * general error and populated for invalid request fields.
 */
public record ApiError(int status, String message, LocalDateTime timestamp, String path,
        Map<String, String> fieldErrors) {
}
