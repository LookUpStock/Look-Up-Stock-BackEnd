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

// Heroku terminates TLS at the router and reports the original protocol in X-Forwarded-Proto; without it (local, tests) nothing is redirected.
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
        // 308 instead of 301 keeps the method and body.
        response.setStatus(HttpStatus.PERMANENT_REDIRECT.value());
        response.setHeader(HttpHeaders.LOCATION, target);
    }

    // The header may contain several values ("http, https"); the first one counts.
    private static String firstValue(String header) {
        if (header == null) {
            return null;
        }
        int comma = header.indexOf(',');
        return (comma < 0 ? header : header.substring(0, comma)).trim();
    }
}
