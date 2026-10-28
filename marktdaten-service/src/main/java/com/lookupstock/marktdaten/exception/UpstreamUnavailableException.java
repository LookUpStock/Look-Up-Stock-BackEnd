package com.lookupstock.marktdaten.exception;

/** Twelve Data ist nicht erreichbar oder lehnt die Anfrage ab (Antwort 503 an den core-service). */
public class UpstreamUnavailableException extends RuntimeException {

    /** Gleicher Text wie im core-service (Messages.UPSTREAM_UNAVAILABLE). */
    public static final String MESSAGE = "Kursdaten sind gerade nicht verfügbar.";

    public UpstreamUnavailableException(Throwable cause) {
        super(MESSAGE, cause);
    }
}
