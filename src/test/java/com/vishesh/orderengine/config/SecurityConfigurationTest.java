package com.vishesh.orderengine.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

@SpringBootTest
public class SecurityConfigurationTest {
    @Autowired
    private AuthenticationManager authenticationManager;

    @Test
    public void authenticatesWriterWithValidCredentials() {
        String username = "order-writer";
        String password = "writer-password";
        Authentication request = UsernamePasswordAuthenticationToken.unauthenticated(username, password);
        Authentication result = authenticationManager.authenticate(request);

        assertTrue(result.isAuthenticated());
        assertEquals("order-writer", result.getName());
        assertTrue(result.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ORDER_WRITER")));
    }

    @Test
    public void rejectsWriterWithInvalidPassword() {
        String username = "order-writer";
        String password = "wrong-password";
        Authentication request = UsernamePasswordAuthenticationToken.unauthenticated(username, password);
        assertThrows(BadCredentialsException.class, () -> authenticationManager.authenticate(request));
    }
}
