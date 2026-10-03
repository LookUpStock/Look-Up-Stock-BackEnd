package com.lookupstock.backend.exception;

import java.util.List;

public class ValidationException extends RuntimeException {

    public record FieldError(String field, String message) {}

    private final List<FieldError> errors;

    public ValidationException(String message, List<FieldError> errors) {
        super(message);
        this.errors = List.copyOf(errors);
    }

    public List<FieldError> getErrors() {
        return errors;
    }
}