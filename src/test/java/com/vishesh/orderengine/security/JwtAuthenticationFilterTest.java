package com.vishesh.orderengine.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;

/*
 * Isolated filter tests prove the three possible outcomes before the full
 * Spring security chain is exercised by OrderSecurityTest.
 */
public class JwtAuthenticationFilterTest {
    @AfterEach
    public void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    public void authenticatesRequestWhenBearerTokenIsValid() throws Exception {
        JwtTokenService jwtTokenService = mock(JwtTokenService.class);
        FilterChain filterChain = mock(FilterChain.class);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtTokenService);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        request.addHeader("Authorization", "Bearer valid-token");

        when(jwtTokenService.parse("valid-token"))
                .thenReturn(new JwtPrincipal(
                        "order-writer",
                        Set.of("ROLE_ORDER_WRITER")));

        filter.doFilter(request, response, filterChain);
        verify(filterChain).doFilter(request, response);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertTrue(authentication != null);
        assertEquals("order-writer", authentication.getName());
        assertTrue(authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ORDER_WRITER")));
    }

    @Test
    public void continuesFilterChainWhenBearerHeaderIsMissing() throws Exception {
        JwtTokenService jwtTokenService = mock(JwtTokenService.class);
        FilterChain filterChain = mock(FilterChain.class);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtTokenService);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);
        verify(filterChain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(jwtTokenService);
    }

    @Test
    public void rejectsRequestWhenBearerTokenIsInvalid() throws Exception {
        JwtTokenService jwtTokenService = mock(JwtTokenService.class);
        FilterChain filterChain = mock(FilterChain.class);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtTokenService);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        request.addHeader("Authorization", "Bearer invalid-token");
        when(jwtTokenService.parse("invalid-token")).thenThrow(new JwtException("Token is invalid"));

        filter.doFilter(request, response, filterChain);
        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(jwtTokenService).parse("invalid-token");
        verifyNoInteractions(filterChain);
    }
}
