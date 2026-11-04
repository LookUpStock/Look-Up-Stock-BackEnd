package com.lookupstock.backend.config;

import java.io.IOException;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Leitet Aufrufe über http mit 308 auf dieselbe Adresse mit https um (NF-05, BE #135).
 * Heroku beendet TLS am Router und meldet das ursprüngliche Protokoll im Header X-Forwarded-Proto.
 * Ohne diesen Header (lokal, Tests) wird nichts umgeleitet.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class HttpsRedirectFilter extends OncePerRequestFilter {

    static final String FORWARDED_PROTO = "X-Forwarded-Proto";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!"http".equalsIgnoreCase(firstValue(request.getHeader(FORWARDED_PROTO)))) {
            chain.doFilter(request, response);
            return;
        }
        String host = request.getHeader(HttpHeaders.HOST);
        if (host == null || host.isBlank()) {
            host = request.getServerName();
        }
        String query = request.getQueryString();
        String target = "https://" + host + request.getRequestURI() + (query == null ? "" : "?" + query);
        // 308 statt 301: Methode und Body bleiben bei der Umleitung erhalten
        response.setStatus(HttpStatus.PERMANENT_REDIRECT.value());
        response.setHeader(HttpHeaders.LOCATION, target);
    }

    // Der Header kann mehrere Werte enthalten ("http, https"); maßgeblich ist der erste
    private static String firstValue(String header) {
        if (header == null) {
            return null;
        }
        int comma = header.indexOf(',');
        return (comma < 0 ? header : header.substring(0, comma)).trim();
    }
}
