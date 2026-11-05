package com.lookupstock.backend.controller;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import com.lookupstock.backend.exception.Messages;
import com.lookupstock.backend.exception.UnauthorizedException;
import com.lookupstock.backend.model.User;
import com.lookupstock.backend.service.AuthService;

public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    private static final String BEARER_PREFIX = "Bearer ";

    // ObjectProvider so controller tests without an AuthService mock can start.
    private final ObjectProvider<AuthService> authService;

    public CurrentUserArgumentResolver(ObjectProvider<AuthService> authService) {
        this.authService = authService;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUser.class)
                && User.class.equals(parameter.getParameterType());
    }

    @Override
    public User resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        String token = extractToken(webRequest.getHeader(HttpHeaders.AUTHORIZATION));
        if (token == null) {
            throw new UnauthorizedException(Messages.NOT_LOGGED_IN);
        }
        User user = authService.getObject().authenticate(token);
        if (user == null) {
            throw new UnauthorizedException(Messages.NOT_LOGGED_IN);
        }
        return user;
    }

    public static String extractToken(String header) {
        if (header == null || !header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return null;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }
}
