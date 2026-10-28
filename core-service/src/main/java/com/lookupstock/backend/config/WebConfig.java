package com.lookupstock.backend.config;

import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.lookupstock.backend.controller.CurrentUserArgumentResolver;
import com.lookupstock.backend.service.AuthService;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final ObjectProvider<AuthService> authService;

    public WebConfig(ObjectProvider<AuthService> authService) {
        this.authService = authService;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new CurrentUserArgumentResolver(authService));
    }
}
