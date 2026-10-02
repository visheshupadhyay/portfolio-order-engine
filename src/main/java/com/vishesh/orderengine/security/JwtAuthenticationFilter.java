package com.vishesh.orderengine.security;

import java.io.IOException;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/*
 * Turns a valid Bearer token into Spring Security's authenticated user.
 * Endpoint role rules remain in SecurityConfiguration, after this filter runs.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenService jwtTokenService;

    public JwtAuthenticationFilter(JwtTokenService jwtTokenService) {
        this.jwtTokenService = jwtTokenService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            // Public endpoints may legitimately have no token; later rules decide access.
            filterChain.doFilter(request, response);
            return;
        }

        String jwtToken = header.substring(7);
        try {

            JwtPrincipal jwtPrincipal = jwtTokenService.parse(jwtToken);
            // Spring's hasRole rules expect authorities such as ROLE_ORDER_WRITER.
            List<SimpleGrantedAuthority> authorities = jwtPrincipal.roles().stream()
                    .map(SimpleGrantedAuthority::new)
                    .toList();
            SecurityContextHolder.getContext().setAuthentication(
                    UsernamePasswordAuthenticationToken.authenticated(jwtPrincipal.username(), null, authorities));
            filterChain.doFilter(request, response);
        } catch (JwtException e) {
            // A supplied but invalid token must not reach a protected controller.
            SecurityContextHolder.clearContext();
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid access token");
        }
    }
}
