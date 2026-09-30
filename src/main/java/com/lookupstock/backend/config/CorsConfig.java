package com.lookupstock.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// KI-Log Nr. XX: CORS-Konfiguration mit KI (Claude) erstellt, vom Team geprüft

/**
 * Erlaubt dem Frontend, die REST-API unter /api/** aufzurufen.
 * Die erlaubten Adressen stehen in application.properties (app.cors.allowed-origins).
 */

@Configuration

public class CorsConfig implements WebMvcConfigurer {

    private final String[] allowedOrigins;

    public CorsConfig(@Value("${app.cors.allowed-origins}") String[] allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS");
    }
}