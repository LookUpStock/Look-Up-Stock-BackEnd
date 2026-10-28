package com.lookupstock.backend.graphql;

import java.util.Map;

import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.stereotype.Component;

import com.lookupstock.backend.exception.NotFoundException;
import com.lookupstock.backend.exception.UnauthorizedException;
import com.lookupstock.backend.exception.UpstreamUnavailableException;
import com.lookupstock.backend.exception.ValidationException;

import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;

/**
 * Übersetzt die Exceptions der Services in GraphQL-Fehler.
 * Meldung und Code entsprechen dem REST-Fehlerformat (GlobalExceptionHandler, NF-15).
 */
@Component
public class GraphQlExceptionResolver extends DataFetcherExceptionResolverAdapter {

    @Override
    protected GraphQLError resolveToSingleError(Throwable ex, DataFetchingEnvironment env) {
        if (ex instanceof UnauthorizedException) {
            return error(env, ex, ErrorType.UNAUTHORIZED, "UNAUTHORIZED");
        }
        if (ex instanceof NotFoundException) {
            return error(env, ex, ErrorType.NOT_FOUND, "NOT_FOUND");
        }
        if (ex instanceof ValidationException) {
            return error(env, ex, ErrorType.BAD_REQUEST, "VALIDATION_ERROR");
        }
        if (ex instanceof UpstreamUnavailableException) {
            return error(env, ex, ErrorType.INTERNAL_ERROR, "UPSTREAM_UNAVAILABLE");
        }
        // Unbekannte Fehler übernimmt Spring mit einer allgemeinen Meldung ohne interne Details
        return null;
    }

    private static GraphQLError error(DataFetchingEnvironment env, Throwable ex, ErrorType type, String code) {
        return GraphqlErrorBuilder.newError(env)
                .errorType(type)
                .message(ex.getMessage())
                .extensions(Map.of("code", code))
                .build();
    }
}