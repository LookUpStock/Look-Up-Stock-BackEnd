package com.lookupstock.backend.exception;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.lookupstock.backend.dto.ErrorDetail;
import com.lookupstock.backend.dto.ErrorResponse;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidation(ValidationException e) {
        List<ErrorDetail> details = e.getErrors().stream()
                .map(err -> new ErrorDetail(err.field(), err.message()))
                .toList();
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of("VALIDATION_ERROR", e.getMessage(), details));
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("NOT_FOUND", e.getMessage(), List.of()));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(ConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("CONFLICT", e.getMessage(), List.of()));
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorized(UnauthorizedException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of("UNAUTHORIZED", e.getMessage(), List.of()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of("VALIDATION_ERROR", "Die Anfrage enthält ungültige Daten.", List.of()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of("VALIDATION_ERROR", "Ungültiger Wert für " + e.getName() + ".", List.of()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleOther(Exception e) {
        // Spring-eigene Fehler (unbekannte URL, falsche Methode, falscher Content-Type, ...)
        // bringen ihren HTTP-Status selbst mit und sind kein Serverfehler.
        if (e instanceof org.springframework.web.ErrorResponse springError
                && springError.getStatusCode().is4xxClientError()) {
            int status = springError.getStatusCode().value();
            ErrorResponse body = switch (status) {
                case 404 ->
                    ErrorResponse.of("NOT_FOUND", "Die angeforderte Ressource wurde nicht gefunden.", List.of());
                case 405 ->
                    ErrorResponse.of("METHOD_NOT_ALLOWED", "Diese HTTP-Methode ist hier nicht erlaubt.", List.of());
                case 415 ->
                    ErrorResponse.of("UNSUPPORTED_MEDIA_TYPE", "Der Content-Type muss application/json sein.", List.of());
                default ->
                    ErrorResponse.of("BAD_REQUEST", "Die Anfrage ist ungültig.", List.of());
            };
            return ResponseEntity.status(status)
                    .headers(springError.getHeaders())
                    .body(body);
        }

        log.error("Unerwarteter Fehler", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of("INTERNAL_ERROR", "Ein unerwarteter Fehler ist aufgetreten.", List.of()));
    }

    @ExceptionHandler(UpstreamUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleUpstream(UpstreamUnavailableException e) {
        log.warn("Finance-API not available", e);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ErrorResponse.of("UPSTREAM_UNAVAILABLE", e.getMessage(), List.of()));
    }
}
