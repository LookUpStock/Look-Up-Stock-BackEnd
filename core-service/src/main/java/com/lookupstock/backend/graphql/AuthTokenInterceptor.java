package com.lookupstock.backend.graphql;

import java.util.Map;

import org.springframework.graphql.server.WebGraphQlInterceptor;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import com.lookupstock.backend.controller.CurrentUserArgumentResolver;

import reactor.core.publisher.Mono;

@Component
public class AuthTokenInterceptor implements WebGraphQlInterceptor {

    public static final String TOKEN_KEY = "authToken";

    @Override
    public Mono<WebGraphQlResponse> intercept(WebGraphQlRequest request, Chain chain) {
        String token = CurrentUserArgumentResolver.extractToken(
                request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
        if (token != null) {
            request.configureExecutionInput((input, builder) ->
                    builder.graphQLContext(Map.<String, Object>of(TOKEN_KEY, token)).build());
        }
        return chain.next(request);
    }
}