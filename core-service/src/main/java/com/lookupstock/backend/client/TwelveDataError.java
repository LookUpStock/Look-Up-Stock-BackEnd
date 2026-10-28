package com.lookupstock.backend.client;

/**
 * Einordnung der Fehlercodes von Twelve Data.
 * Twelve Data antwortet bei Fehlern meist mit HTTP 200 und {"status":"error","code":…,"message":…}.
 */
enum TwelveDataError {

    /** 401: API-Key fehlt oder ist ungültig. */
    INVALID_KEY,
    /** 403: Der Tarif enthält das Symbol bzw. die Börse nicht (z. B. Xetra im kostenlosen Tarif, Docs #80). */
    PLAN_RESTRICTED,
    /** 429: Kontingent von Anfragen pro Minute bzw. pro Tag ausgeschöpft. */
    RATE_LIMIT,
    /** 400/404: Symbol bzw. Börse unbekannt oder keine Daten vorhanden. */
    NOT_FOUND,
    /** Alle übrigen Fehler, z. B. 5xx beim Anbieter. */
    OTHER;

    static TwelveDataError fromCode(Integer code) {
        if (code == null) {
            return OTHER;
        }
        return switch (code) {
            case 401 -> INVALID_KEY;
            case 403 -> PLAN_RESTRICTED;
            case 429 -> RATE_LIMIT;
            case 400, 404 -> NOT_FOUND;
            default -> OTHER;
        };
    }
}
