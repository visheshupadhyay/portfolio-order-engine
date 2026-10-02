package com.vishesh.orderengine.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;

/*
 * Springdoc finds this OpenAPI bean while generating /v3/api-docs and
 * Swagger UI. It describes the API for clients; it does not enforce security.
 * SecurityConfiguration remains responsible for enforcing Bearer JWT authentication.
 */
@Configuration
public class OpenApiConfiguration {

    @Bean
    public OpenAPI orderEngineOpenApi() {
        return new OpenAPI()
                // "bearerAuth" is the documentation name used by OrderController.
                .components(new Components()
                        .addSecuritySchemes(
                                "bearerAuth",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")))
                .info(new Info()
                        .title("Order Engine API")
                        .version("1.0")
                        .description("API for creating, finding, listing, and paying orders."));
    }
}
