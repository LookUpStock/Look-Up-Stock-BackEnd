package com.lookupstock.marktdaten.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.lookupstock.marktdaten.model.SearchHit;
import com.lookupstock.marktdaten.exception.UpstreamUnavailableException;

class SearchCacheTest {

    private static final Instant START = Instant.parse("2026-10-17T12:00:00Z");

    // Aktuelle Uhrzeit im Test; kann vorgestellt werden, um den Ablauf zu prüfen
    private Instant now;
    private SearchCache cache;

    // Zählt, wie oft die Finanzdaten-API abgefragt wurde
    private AtomicInteger loaderCalls;

    @BeforeEach
    void setUp() {
        now = START;
        Clock clock = mock(Clock.class);
        when(clock.instant()).thenAnswer(invocation -> now);
        cache = new SearchCache(clock);
        loaderCalls = new AtomicInteger();
    }

    // ---------- Cache-Treffer ----------

    @Test
    void getOrLoad_sameQueryTwice_loadsOnlyOnce() {
        List<SearchHit> first = cache.getOrLoad("apple", loader(hit("AAPL")));
        List<SearchHit> second = cache.getOrLoad("apple", loader(hit("OTHER")));

        // Die zweite Abfrage kommt aus dem Cache, der zweite loader wird nie aufgerufen
        assertThat(loaderCalls).hasValue(1);
        assertThat(second).isEqualTo(first).extracting(SearchHit::symbol).containsExactly("AAPL");
    }

    @Test
    void getOrLoad_differentCaseAndSpaces_sharesOneEntry() {
        cache.getOrLoad("Apple", loader(hit("AAPL")));
        cache.getOrLoad("  aPPle ", loader(hit("AAPL")));

        assertThat(loaderCalls).hasValue(1);
    }

    @Test
    void getOrLoad_differentQueries_haveSeparateEntries() {
        cache.getOrLoad("apple", loader(hit("AAPL")));
        List<SearchHit> msci = cache.getOrLoad("msci", loader(hit("EUNL")));

        assertThat(loaderCalls).hasValue(2);
        assertThat(msci).extracting(SearchHit::symbol).containsExactly("EUNL");
    }

    @Test
    void getOrLoad_emptyResult_isCachedToo() {
        cache.getOrLoad("xyzxyz", loader());
        List<SearchHit> second = cache.getOrLoad("xyzxyz", loader(hit("AAPL")));

        // Auch "keine Treffer" ist eine gültige Antwort und wird nicht erneut abgefragt
        assertThat(loaderCalls).hasValue(1);
        assertThat(second).isEmpty();
    }

    // ---------- Ablauf nach 24 Stunden ----------

    @Test
    void getOrLoad_justBefore24Hours_isStillCached() {
        cache.getOrLoad("apple", loader(hit("AAPL")));

        now = START.plus(SearchCache.TIME_TO_LIVE).minus(Duration.ofSeconds(1));
        cache.getOrLoad("apple", loader(hit("AAPL")));

        assertThat(loaderCalls).hasValue(1);
    }

    @Test
    void getOrLoad_after24Hours_loadsAgain() {
        cache.getOrLoad("apple", loader(hit("AAPL")));

        now = START.plus(SearchCache.TIME_TO_LIVE);
        List<SearchHit> refreshed = cache.getOrLoad("apple", loader(hit("AAPL"), hit("APLE")));

        // Der Eintrag ist abgelaufen, die neuen Treffer ersetzen die alten
        assertThat(loaderCalls).hasValue(2);
        assertThat(refreshed).extracting(SearchHit::symbol).containsExactly("AAPL", "APLE");
    }

    // ---------- Fehler der Finanzdaten-API ----------

    @Test
    void getOrLoad_loaderFails_storesNothing() {
        UpstreamUnavailableException failure =
                new UpstreamUnavailableException(null);

        assertThatThrownBy(() -> cache.getOrLoad("apple", () -> {
            loaderCalls.incrementAndGet();
            throw failure;
        })).isSameAs(failure);

        // Beim nächsten Versuch wird erneut abgefragt, statt einen Fehler zu liefern
        List<SearchHit> retry = cache.getOrLoad("apple", loader(hit("AAPL")));
        assertThat(loaderCalls).hasValue(2);
        assertThat(retry).extracting(SearchHit::symbol).containsExactly("AAPL");
    }

    // ---------- Hilfsmethoden ----------

    // Liefert einen loader, der die übergebenen Treffer zurückgibt und seine Aufrufe zählt
    private Supplier<List<SearchHit>> loader(SearchHit... hits) {
        return () -> {
            loaderCalls.incrementAndGet();
            return List.of(hits);
        };
    }

    // Erzeugt einen Suchtreffer mit Standardwerten
    private static SearchHit hit(String symbol) {
        return new SearchHit(symbol, symbol + " Inc.", "NASDAQ", "XNAS", "USD", "Common Stock");
    }
}