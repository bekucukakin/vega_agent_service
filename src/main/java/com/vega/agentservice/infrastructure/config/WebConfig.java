package com.vega.agentservice.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Global CORS configuration for Vega Agent Service.
 * Allows requests from the Vega Repos frontend (Vite dev server on :5173
 * and any production origin configured via CORS_ALLOWED_ORIGINS env var).
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String extra = System.getenv("CORS_ALLOWED_ORIGINS");

        registry.addMapping("/api/**")
                .allowedOriginPatterns(
                    "http://localhost:5173",
                    "http://localhost:5174",
                    "http://localhost:8081",
                    "http://127.0.0.1:5173",
                    extra != null && !extra.isBlank() ? extra : "http://localhost:5173"
                )
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
