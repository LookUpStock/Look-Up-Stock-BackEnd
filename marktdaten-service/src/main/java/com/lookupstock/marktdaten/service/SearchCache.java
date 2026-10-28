package com.lookupstock.marktdaten.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import com.lookupstock.marktdaten.model.SearchHit;

/**
 * Zwischenspeicher für Suchergebnisse der Finanzdaten-API (NF-02, AT-02-08).
 * Derselbe Suchbegriff wird 24 Stunden lang aus dem Arbeitsspeicher beantwortet,
 * ohne Twelve Data erneut abzufragen. Nach einem Neustart des marktdaten-service ist der Cache leer (BE #106: aus dem core-service hierher verschoben).
 */
@Component
public class SearchCache {

    // Gültigkeitsdauer eines Eintrags laut NF-02
    static final Duration TIME_TO_LIVE = Duration.ofHours(24);

    // Ein Eintrag merkt sich die Treffer und den Zeitpunkt der Abfrage
    private record CacheEntry(Instant fetchedAt, List<SearchHit> hits) {
    }

    // ConcurrentHashMap, weil mehrere Anfragen gleichzeitig suchen können
    private final Map<String, CacheEntry> entries = new ConcurrentHashMap<>();
    private final Clock clock;

    public SearchCache(Clock clock) {
        this.clock = clock;
    }

    /**
     * Liefert die Treffer zu einem Suchbegriff aus dem Cache. Fehlt der Eintrag oder ist er
     * älter als 24 Stunden, werden die Treffer über den loader neu abgefragt und gespeichert.
     *
     * @param query  Suchbegriff, wie ihn der Nutzer eingegeben hat
     * @param loader fragt die Treffer bei der Finanzdaten-API ab
     * @return Treffer in der Reihenfolge des Anbieters, nicht veränderbar
     */
    public List<SearchHit> getOrLoad(String query, Supplier<List<SearchHit>> loader) {
        String key = normalize(query);
        Instant now = clock.instant();

        // Cache-Treffer: gültiger Eintrag vorhanden, keine Abfrage bei Twelve Data
        CacheEntry entry = entries.get(key);
        if (entry != null && isFresh(entry, now)) {
            return entry.hits();
        }

        // Cache-Fehlschlag: neu abfragen; wirft der loader eine Exception (z. B. 503), wird nichts gespeichert
        List<SearchHit> hits = List.copyOf(loader.get());
        entries.put(key, new CacheEntry(now, hits));
        removeExpired(now);
        return hits;
    }

    // Ein Eintrag ist gültig, solange seit der Abfrage weniger als 24 Stunden vergangen sind
    private static boolean isFresh(CacheEntry entry, Instant now) {
        return entry.fetchedAt().plus(TIME_TO_LIVE).isAfter(now);
    }

    // Abgelaufene Einträge entfernen, damit der Speicher nicht unbegrenzt wächst
    private void removeExpired(Instant now) {
        entries.values().removeIf(entry -> !isFresh(entry, now));
    }

    // Groß-/Kleinschreibung und Leerzeichen am Rand spielen für die Suche keine Rolle
    private static String normalize(String query) {
        return query.trim().toLowerCase(Locale.ROOT);
    }
}