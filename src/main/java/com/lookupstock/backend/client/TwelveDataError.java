package com.lookupstock.backend.client;

/**
 * Einordnung der Fehlercodes von Twelve Data.
 * Twelve Data antwortet bei Fehlern meist mit HTTP 200 und {"status":"error","code":…,"message":…}.
 */
enum TwelveDataError {

    /** 401/403: API-Key fehlt, ist ungültig oder der Tarif erlaubt den Endpunkt nicht. */
    INVALID_KEY,
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
            case 401, 403 -> INVALID_KEY;
            case 429 -> RATE_LIMIT;
            case 400, 404 -> NOT_FOUND;
            default -> OTHER;
        };
    }
}
