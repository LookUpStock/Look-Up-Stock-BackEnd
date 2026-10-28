package com.lookupstock.backend.graphql;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.stereotype.Component;

import com.lookupstock.backend.exception.ConflictException;
import com.lookupstock.backend.exception.InsufficientDataException;
import com.lookupstock.backend.exception.NotFoundException;
import com.lookupstock.backend.exception.UnauthorizedException;
import com.lookupstock.backend.exception.UpstreamUnavailableException;
import com.lookupstock.backend.exception.ValidationException;

import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;

/**
 * Übersetzt die Exceptions der Services in GraphQL-Fehler.
 * Meldung, Code und Details entsprechen dem REST-Fehlerformat (GlobalExceptionHandler, NF-15).
 * Die Details stehen in extensions.details, z. B. [{"field":"name","message":"…"}].
 */
@Component
public class GraphQlExceptionResolver extends DataFetcherExceptionResolverAdapter {

    @Override
    protected GraphQLError resolveToSingleError(Throwable ex, DataFetchingEnvironment env) {
        if (ex instanceof UnauthorizedException) {
            return error(env, ex, ErrorType.UNAUTHORIZED, "UNAUTHORIZED", List.of());
        }
        if (ex instanceof NotFoundException) {
            return error(env, ex, ErrorType.NOT_FOUND, "NOT_FOUND", List.of());
        }
        if (ex instanceof ValidationException validation) {
            List<Map<String, Object>> details = validation.getErrors().stream()
                    .map(e -> detail(e.field(), null, null, e.message()))
                    .toList();
            return error(env, ex, ErrorType.BAD_REQUEST, "VALIDATION_ERROR", details);
        }
        if (ex instanceof InsufficientDataException insufficient) {
            // REST: 422; GraphQL kennt keinen eigenen Typ dafür, unterschieden wird über den Code
            List<Map<String, Object>> details = insufficient.getAssets().stream()
                    .map(a -> detail("assetId", a.assetId(), a.name(), a.detail()))
                    .toList();
            return error(env, ex, ErrorType.BAD_REQUEST, "INSUFFICIENT_DATA", details);
        }
        if (ex instanceof ConflictException) {
            return error(env, ex, ErrorType.BAD_REQUEST, "CONFLICT", List.of());
        }
        if (ex instanceof UpstreamUnavailableException) {
            return error(env, ex, ErrorType.INTERNAL_ERROR, "UPSTREAM_UNAVAILABLE", List.of());
        }
        // Unbekannte Fehler übernimmt Spring mit einer allgemeinen Meldung ohne interne Details
        return null;
    }

    private static GraphQLError error(DataFetchingEnvironment env, Throwable ex, ErrorType type, String code,
                                      List<Map<String, Object>> details) {
        Map<String, Object> extensions = new LinkedHashMap<>();
        extensions.put("code", code);
        if (!details.isEmpty()) {
            extensions.put("details", details);
        }
        return GraphqlErrorBuilder.newError(env)
                .errorType(type)
                .message(ex.getMessage())
                .extensions(extensions)
                .build();
    }

    // Felder ohne Wert weglassen, wie @JsonInclude(NON_NULL) bei REST
    private static Map<String, Object> detail(String field, Long assetId, String name, String message) {
        Map<String, Object> detail = new LinkedHashMap<>();
        if (field != null) {
            detail.put("field", field);
        }
        if (assetId != null) {
            detail.put("assetId", assetId);
        }
        if (name != null) {
            detail.put("name", name);
        }
        if (message != null) {
            detail.put("message", message);
        }
        return detail;
    }
}
