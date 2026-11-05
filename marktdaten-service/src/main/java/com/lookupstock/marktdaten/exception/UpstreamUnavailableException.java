package com.lookupstock.marktdaten.exception;

public class UpstreamUnavailableException extends RuntimeException {

    public static final String MESSAGE = "Kursdaten sind gerade nicht verfügbar.";

    public UpstreamUnavailableException(Throwable cause) {
        super(MESSAGE, cause);
    }
}
