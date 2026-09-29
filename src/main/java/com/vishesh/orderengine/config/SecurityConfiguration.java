package com.vishesh.orderengine.config;

import java.util.List;

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
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

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
                // Answer a browser's CORS preflight before authentication or controller routing.
                .cors(Customizer.withDefaults())
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

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        /*
         * Spring Security's CORS support looks up this bean by its type.
         * It grants browser permission only to our local frontend and only
         * for the order API; authentication and role checks still happen
         * afterwards for the real request.
         */
        CorsConfiguration corsConfiguration = new CorsConfiguration();
        corsConfiguration.setAllowedOrigins(List.of("http://localhost:3000"));
        corsConfiguration.setAllowedMethods(List.of("GET", "POST"));
        corsConfiguration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-CSRF-TOKEN"));
        corsConfiguration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/orders/**", corsConfiguration);
        return source;
    }
}
