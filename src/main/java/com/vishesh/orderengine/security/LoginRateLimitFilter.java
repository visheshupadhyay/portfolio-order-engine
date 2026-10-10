package com.vishesh.orderengine.security;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import io.github.resilience4j.ratelimiter.RateLimiter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class LoginRateLimitFilter extends OncePerRequestFilter {

    private final RateLimiter rateLimiter;
    private final LoginRateLimitMetrics loginRateLimitMetrics;

    public LoginRateLimitFilter(RateLimiter rateLimiter, LoginRateLimitMetrics loginRateLimitMetrics) {
        this.rateLimiter = rateLimiter;
        this.loginRateLimitMetrics = loginRateLimitMetrics;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (rateLimiter.acquirePermission()) {
            filterChain.doFilter(request, response);
        } else {
            // Record only the branch that actually returns 429. This lets Grafana
            // distinguish a genuine attack/burst from ordinary login traffic.
            loginRateLimitMetrics.recordRejected();
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value()); // HTTP status 429
            response.setContentType(MediaType.APPLICATION_JSON_VALUE); // Content-Type application/json
            response.setCharacterEncoding("UTF-8");
            String jsonResponse = "{\"error\": \"Too Many Requests\", \"message\": \"Rate limit exceeded. Please try again later.\"}";
            response.getWriter().write(jsonResponse);
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        String uri = request.getRequestURI();

        boolean isLoginPath = uri.equals("/auth/login");

        return !request.getMethod().equals("POST") || !isLoginPath;
    }
}
