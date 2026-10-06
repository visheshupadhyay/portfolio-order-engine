package com.vishesh.orderengine.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import jakarta.servlet.FilterChain;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

public class OrderWriteRateLimitFilterTest {
    @Test
    public void rejectsSecondOrderWriteWhenRateLimitIsExhausted() throws Exception {
        // 1. Your specific rate limiter configuration
        RateLimiterConfig rateConfig = RateLimiterConfig.custom()
                .limitForPeriod(1)
                .limitRefreshPeriod(Duration.ofMinutes(1))
                .timeoutDuration(Duration.ZERO)
                .build();

        // 2. Define the isolated temporary test name
        String testName = "order-writes-test";

        // 3. Create a clean registry to keep this temporary limiter separate from
        // production
        RateLimiterRegistry registry = RateLimiterRegistry.of(rateConfig);

        // 4. Instantiate the real temporary limiter using the test name and custom
        // config
        RateLimiter tempLimiter = registry.rateLimiter(testName, rateConfig);

        OrderWriteRateLimitFilter rateLimitFilter = new OrderWriteRateLimitFilter(tempLimiter);
        FilterChain filterChain = mock(FilterChain.class);

        MockHttpServletRequest firstRequest = new MockHttpServletRequest("POST", "/orders");
        MockHttpServletResponse firstResponse = new MockHttpServletResponse();
        rateLimitFilter.doFilter(firstRequest, firstResponse, filterChain);
        verify(filterChain).doFilter(firstRequest, firstResponse);
    
        MockHttpServletRequest secondRequest = new MockHttpServletRequest("POST", "/orders");
        MockHttpServletResponse secondResponse = new MockHttpServletResponse();
        rateLimitFilter.doFilter(secondRequest, secondResponse, filterChain);
        verifyNoMoreInteractions(filterChain);


        assertEquals(429, secondResponse.getStatus());
        assertTrue(secondResponse.getContentAsString().contains("Rate limit exceeded"));

    }

    @Test
    public void doesNotConsumeWritePermitForOrderRead() throws Exception {
        // 1. Your specific rate limiter configuration
        RateLimiterConfig rateConfig = RateLimiterConfig.custom()
                .limitForPeriod(1)
                .limitRefreshPeriod(Duration.ofMinutes(1))
                .timeoutDuration(Duration.ZERO)
                .build();

        // 2. Define the isolated temporary test name
        String testName = "order-writes-test";

        // 3. Create a clean registry to keep this temporary limiter separate from
        // production
        RateLimiterRegistry registry = RateLimiterRegistry.of(rateConfig);

        // 4. Instantiate the real temporary limiter using the test name and custom
        // config
        RateLimiter tempLimiter = registry.rateLimiter(testName, rateConfig);

        OrderWriteRateLimitFilter rateLimitFilter = new OrderWriteRateLimitFilter(tempLimiter);
        FilterChain filterChain = mock(FilterChain.class);

        MockHttpServletRequest firstRequest = new MockHttpServletRequest("GET", "/orders");
        MockHttpServletResponse firstResponse = new MockHttpServletResponse();
        rateLimitFilter.doFilter(firstRequest, firstResponse, filterChain);
        assertEquals(200, firstResponse.getStatus());
        verify(filterChain).doFilter(firstRequest, firstResponse);
    
        MockHttpServletRequest secondRequest = new MockHttpServletRequest("POST", "/orders");
        MockHttpServletResponse secondResponse = new MockHttpServletResponse();
        rateLimitFilter.doFilter(secondRequest, secondResponse, filterChain);
        verify(filterChain).doFilter(secondRequest, secondResponse);
        assertEquals(200, secondResponse.getStatus());
    }
}
