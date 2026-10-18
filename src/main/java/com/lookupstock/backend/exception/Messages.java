package com.lookupstock.backend.exception;

import java.math.BigDecimal;
import java.util.List;

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

    // Portfolio
    public static final String NAME_INVALID = "Bitte einen Namen mit höchstens 100 Zeichen eingeben.";
    public static final String POSITIONS_EMPTY = "Bitte mindestens eine Anlage hinzufügen.";
    public static final String WEIGHT_NOT_POSITIVE = "Jede Gewichtung muss größer als 0 % sein.";
    public static final String WEIGHT_TOO_MANY_DECIMALS = "Gewichtungen dürfen höchstens 2 Nachkommastellen haben.";
    public static final String WEIGHT_SUM = "Die Gewichtungen müssen zusammen 100 % ergeben.";
    public static final String DUPLICATE_ASSET = "Jede Anlage darf nur einmal im Portfolio enthalten sein.";
    public static final String ASSET_NOT_FOUND = "Eine Anlage wurde nicht gefunden.";
    public static final String PORTFOLIO_NOT_FOUND = "Portfolio nicht gefunden.";

    // Long statt long: assetId kann im Request fehlen (null)
    public static String unknownAssetDetail(Long assetId) {
        return "Unbekannte assetId " + assetId + ".";
    }

    // Format wie bisher in Portfolio.validate(): ohne unnötige Nullen, Dezimalpunkt
    public static String weightSumDetail(BigDecimal sum) {
        return "Summe der Gewichtungen ist " + sum.stripTrailingZeros().toPlainString() + " %.";
    }

    // Simulation (US-04)
    public static final String PORTFOLIO_ID_REQUIRED = "Bitte ein Portfolio auswählen.";
    public static final String PARAMETERS_REQUIRED = "Bitte alle Pflichtfelder mit Werten ab 0 ausfüllen.";
    public static final String HORIZON_INVALID = "Der Anlagehorizont muss eine ganze Zahl von 1 bis 40 Jahren sein.";
    public static final String PATHS_INVALID = "Die Anzahl der Simulationspfade muss zwischen 1.000 und 50.000 liegen.";
    public static final String SEED_INVALID = "Der Seed muss eine ganze Zahl sein.";
    public static final String COMPARISON_ITEMS_REQUIRED = "Bitte genau zwei Einträge zum Vergleich auswählen.";
    public static final String COMPARISON_ITEM_INVALID = "Jeder Eintrag braucht genau eine portfolioId oder assetId.";
    public static final String INSUFFICIENT_DATA_DETAIL = "Weniger als 3 Jahre Kursdaten.";
    public static final String NO_PRICE_DATA_DETAIL = "Keine Kursdaten beim Anbieter verfügbar.";

    // E6: alle betroffenen Anlagen werden mit Namen genannt
    public static String insufficientData(List<String> assetNames) {
        return "Für folgende Anlagen liegen weniger als 3 Jahre Kursdaten vor: "
                + String.join(", ", assetNames) + ".";
    }

    // Docs #80: Anbieter liefert gar keine Kurse (z. B. Börse im Tarif nicht enthalten) – nicht „zu kurze Historie“
    public static String noPriceData(List<String> assetNames) {
        return "Für folgende Anlagen liefert der Kursdatenanbieter keine Kurse: "
                + String.join(", ", assetNames) + ". Bitte die Anlage an einer anderen Börse auswählen.";
    }

    // Detail bei Zahlenwerten, wie im API-Design („Wert: 2.5“)
    public static String valueDetail(Object value) {
        return "Wert: " + value;
    }
}
