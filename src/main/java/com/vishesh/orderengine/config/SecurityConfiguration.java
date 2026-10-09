package com.vishesh.orderengine.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.vishesh.orderengine.security.JwtAuthenticationFilter;
import com.vishesh.orderengine.security.LoginRateLimitFilter;
import com.vishesh.orderengine.security.OrderWriteRateLimitFilter;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;

/*
 * Security revision: the filter chain runs before controllers. GET order
 * routes need a reader role; POST order routes need a writer role. This is a
 * stateless Bearer-token API, so authentication comes from the JWT filter and
 * CSRF protection is disabled because browsers do not automatically attach an
 * Authorization header.
 * The in-memory users are learning-only placeholders for a future user store.
 */
@Configuration
public class SecurityConfiguration {

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity httpSecurity,
			JwtAuthenticationFilter jwtAuthenticationFilter, RateLimiterRegistry rateLimiterRegistry)
			throws Exception {

		// Answer a browser's CORS preflight before authentication or controller
		// routing.

		RateLimiter rateLimiter = rateLimiterRegistry.rateLimiter("orderWrites");
		OrderWriteRateLimitFilter rateLimiterFilter = new OrderWriteRateLimitFilter(rateLimiter);

		RateLimiter loginRateLimiter = rateLimiterRegistry.rateLimiter("loginAttempts");
		LoginRateLimitFilter loginRateLimitFilter = new LoginRateLimitFilter(loginRateLimiter);
		return httpSecurity
				// Missing authentication is 401; a valid user without the required role is 403.
				.exceptionHandling(exception -> exception
						.authenticationEntryPoint(
								new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
				.cors(Customizer.withDefaults())
				// Header Bearer tokens are not sent automatically like browser cookies are.
				.csrf(AbstractHttpConfigurer::disable)
				// Each request must bring its own JWT; no server session remembers a login.
				.sessionManagement(session -> session
						.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.addFilterBefore(loginRateLimitFilter, UsernamePasswordAuthenticationFilter.class)
				.addFilterAfter(jwtAuthenticationFilter, LoginRateLimitFilter.class)
				.addFilterAfter(rateLimiterFilter, JwtAuthenticationFilter.class)
				.authorizeHttpRequests(authorize -> authorize
						.requestMatchers("/actuator/health/**").permitAll()
						.requestMatchers("/v3/api-docs/**", "/swagger-ui/**",
								"/swagger-ui.html")
						.permitAll()
						.requestMatchers(HttpMethod.POST, "/auth/login").permitAll()
						.requestMatchers(HttpMethod.GET, "/orders/**").hasRole("ORDER_READER")
						.requestMatchers(HttpMethod.POST, "/orders/**").hasRole("ORDER_WRITER")
						.requestMatchers("/orders/**").authenticated()
						.requestMatchers(
								"/actuator/metrics",
								"/actuator/metrics/**",
								"/actuator/prometheus")
						.hasRole("ORDER_ADMIN")
						.anyRequest().denyAll())
				// Passwords are accepted only by /auth/login, never by normal order routes.
				.httpBasic(AbstractHttpConfigurer::disable)
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

		UserDetails admin = User.withUsername("order-admin")
				.password(passwordEncoder.encode("admin-password"))
				.roles("ORDER_ADMIN")
				.build();

		return new InMemoryUserDetailsManager(orderReader, orderWriter, admin);
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
		corsConfiguration.setAllowedHeaders(List.of("Authorization", "Content-Type"));

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/orders/**", corsConfiguration);
		source.registerCorsConfiguration("/auth/**", corsConfiguration);
		return source;
	}

	@Bean
	public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) {

		return authenticationConfiguration.getAuthenticationManager();
	}
}
