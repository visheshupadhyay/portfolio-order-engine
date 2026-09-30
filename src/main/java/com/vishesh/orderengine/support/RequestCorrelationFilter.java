package com.vishesh.orderengine.support;

import java.io.IOException;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class RequestCorrelationFilter extends OncePerRequestFilter {
    // This is our chosen contract with callers: they may provide a request ID,
    // otherwise the application creates one and returns it in the response.
    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String suppliedRequestId = request.getHeader(REQUEST_ID_HEADER);

        final String requestId;
        if (suppliedRequestId == null || suppliedRequestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        } else {
            requestId = suppliedRequestId;
        }

        // MDC is thread-local logging context, so every log written while this
        // request is handled can include the same request ID automatically.
        MDC.put("requestId", requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        
        try {
            filterChain.doFilter(request, response);
        } finally {
            // Application threads are reused; clearing prevents this request ID
            // from appearing incorrectly in the next request's logs.
            MDC.remove("requestId");
        }

    }

}
