package com.lookupstock.marktdaten.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.lookupstock.marktdaten.exception.UpstreamUnavailableException;

/** Fehler im selben Format wie beim core-service: {"error":{"code":…,"message":…}} (NF-15). */
@RestControllerAdvice
public class ErrorHandler {

    public record ErrorResponse(ErrorBody error) {

        public record ErrorBody(String code, String message) {
        }

        static ErrorResponse of(String code, String message) {
            return new ErrorResponse(new ErrorBody(code, message));
        }
    }

    @ExceptionHandler(UpstreamUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleUpstream(UpstreamUnavailableException e) {
        // Ursache wurde bereits im TwelveDataProvider protokolliert
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ErrorResponse.of("UPSTREAM_UNAVAILABLE", e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleInvalid(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(ErrorResponse.of("VALIDATION_ERROR", e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of("VALIDATION_ERROR", "Ungültiger Wert für " + e.getName() + "."));
    }
}
