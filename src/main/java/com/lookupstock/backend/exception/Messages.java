package com.lookupstock.backend.exception;

public final class Messages {

    private Messages() {
    }

    // Registrierung und Anmeldung
    public static final String PASSWORD_TOO_SHORT = "Das Passwort muss mindestens 8 Zeichen haben.";
    public static final String PASSWORD_TOO_LONG = "Das Passwort darf höchstens 72 Byte haben.";
    public static final String EMAIL_TAKEN = "Diese E-Mail ist bereits registriert.";
    public static final String EMAIL_INVALID = "Bitte gib eine gültige E-Mail-Adresse ein.";
    public static final String INVALID_CREDENTIALS = "E-Mail oder Passwort ist falsch.";
    public static final String NOT_LOGGED_IN = "Bitte melde dich an.";

    // Suche
    public static final String QUERY_TOO_SHORT = "Bitte mindestens 2 Zeichen eingeben.";
    public static final String LIMIT_RANGE = "Der Parameter limit muss zwischen 1 und 25 liegen.";
    public static final String UPSTREAM_UNAVAILABLE = "Kursdaten sind gerade nicht verfügbar.";

    // Allgemeine Fehler (GlobalExceptionHandler)
    public static final String REQUEST_UNREADABLE = "Die Anfrage enthält ungültige Daten.";
    public static final String RESOURCE_NOT_FOUND = "Die angeforderte Ressource wurde nicht gefunden.";
    public static final String METHOD_NOT_ALLOWED = "Diese HTTP-Methode ist hier nicht erlaubt.";
    public static final String UNSUPPORTED_MEDIA_TYPE = "Der Content-Type muss application/json sein.";
    public static final String BAD_REQUEST = "Die Anfrage ist ungültig.";
    public static final String INTERNAL_ERROR = "Ein unerwarteter Fehler ist aufgetreten.";
}
