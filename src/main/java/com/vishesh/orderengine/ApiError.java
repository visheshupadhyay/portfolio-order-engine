package com.vishesh.orderengine;

/* Stable JSON error contract returned by ApiExceptionHandler instead of framework-specific errors. */
public record ApiError(int status, String message) {
    
}
