package com.vishesh.orderengine;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/*
 * Security revision: the filter chain runs before controllers. GET order
 * routes need a reader role; POST order routes need a writer role. CSRF stays
 * enabled because this Basic-auth example can be called by a browser.
 * The in-memory users are learning-only placeholders for a future user store.
 */
@Configuration
public class SecurityConfiguration {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity httpSecurity) throws Exception {
        return httpSecurity
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.GET, "/orders/**").hasRole("ORDER_READER")
                        .requestMatchers(HttpMethod.POST, "/orders/**").hasRole("ORDER_WRITER")
                        .requestMatchers("/orders/**").authenticated()
                        .anyRequest().permitAll())
                .httpBasic(Customizer.withDefaults())
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder passwordEncoder) {
        UserDetails orderReader = User.withUsername("order-reader")
                .password(passwordEncoder.encode("reader-password"))
                .roles("ORDER_READER")
                .build();
        UserDetails orderWriter = User.withUsername("order-writer")
                .password(passwordEncoder.encode("writer-password"))
                .roles("ORDER_WRITER")
                .build();

        return new InMemoryUserDetailsManager(orderReader, orderWriter);
    }
}
