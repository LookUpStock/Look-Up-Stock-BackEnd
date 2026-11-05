package com.lookupstock.marktdaten.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

// Without a configured key every request is rejected instead of leaving the service open.
@Component
public class InternalApiKeyFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Internal-Api-Key";
    static final String UNAUTHORIZED_BODY =
            "{\"error\":{\"code\":\"UNAUTHORIZED\",\"message\":\"Interner Schlüssel fehlt oder ist ungültig.\"}}";

    private static final Logger log = LoggerFactory.getLogger(InternalApiKeyFilter.class);

    private final byte[] expectedKey;

    public InternalApiKeyFilter(@Value("${marktdaten.internal-api-key:}") String internalApiKey) {
        this.expectedKey = internalApiKey == null ? new byte[0] : internalApiKey.trim().getBytes(StandardCharsets.UTF_8);
        if (expectedKey.length == 0) {
            log.error("INTERNAL_API_KEY ist nicht gesetzt – alle Anfragen an /internal werden abgewiesen.");
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // /actuator/health stays reachable without the key for Heroku and monitoring.
        return !request.getRequestURI().startsWith("/internal/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!isValid(request.getHeader(HEADER))) {
            log.warn("Anfrage an {} ohne gültigen internen Schlüssel abgewiesen", request.getRequestURI());
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(UNAUTHORIZED_BODY);
            return;
        }
        chain.doFilter(request, response);
    }

    // Constant-time comparison so the response time reveals nothing about the key.
    private boolean isValid(String givenKey) {
        if (expectedKey.length == 0 || givenKey == null) {
            return false;
        }
        return MessageDigest.isEqual(expectedKey, givenKey.trim().getBytes(StandardCharsets.UTF_8));
    }
}
