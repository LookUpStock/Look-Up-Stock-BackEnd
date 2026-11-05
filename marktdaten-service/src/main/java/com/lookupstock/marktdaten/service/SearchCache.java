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

@Component
public class SearchCache {

    static final Duration TIME_TO_LIVE = Duration.ofHours(24);

    private record CacheEntry(Instant fetchedAt, List<SearchHit> hits) {
    }

    // Concurrent map because several requests may search at the same time.
    private final Map<String, CacheEntry> entries = new ConcurrentHashMap<>();
    private final Clock clock;

    public SearchCache(Clock clock) {
        this.clock = clock;
    }

    public List<SearchHit> getOrLoad(String query, Supplier<List<SearchHit>> loader) {
        String key = normalize(query);
        Instant now = clock.instant();

        CacheEntry entry = entries.get(key);
        if (entry != null && isFresh(entry, now)) {
            return entry.hits();
        }

        List<SearchHit> hits = List.copyOf(loader.get());
        entries.put(key, new CacheEntry(now, hits));
        removeExpired(now);
        return hits;
    }

    private static boolean isFresh(CacheEntry entry, Instant now) {
        return entry.fetchedAt().plus(TIME_TO_LIVE).isAfter(now);
    }

    private void removeExpired(Instant now) {
        entries.values().removeIf(entry -> !isFresh(entry, now));
    }

    private static String normalize(String query) {
        return query.trim().toLowerCase(Locale.ROOT);
    }
}