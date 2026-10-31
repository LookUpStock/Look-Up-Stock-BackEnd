package com.lookupstock.backend.exception;

/**
 * Angemeldeter Nutzer ohne die nötige Rolle (E40), führt zu 403 FORBIDDEN.
 */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
