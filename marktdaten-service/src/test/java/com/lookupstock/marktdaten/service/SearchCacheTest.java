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

    private Instant now;
    private SearchCache cache;

    private AtomicInteger loaderCalls;

    @BeforeEach
    void setUp() {
        now = START;
        Clock clock = mock(Clock.class);
        when(clock.instant()).thenAnswer(invocation -> now);
        cache = new SearchCache(clock);
        loaderCalls = new AtomicInteger();
    }

    @Test
    void getOrLoad_sameQueryTwice_loadsOnlyOnce() {
        List<SearchHit> first = cache.getOrLoad("apple", loader(hit("AAPL")));
        List<SearchHit> second = cache.getOrLoad("apple", loader(hit("OTHER")));

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

        assertThat(loaderCalls).hasValue(1);
        assertThat(second).isEmpty();
    }

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

        assertThat(loaderCalls).hasValue(2);
        assertThat(refreshed).extracting(SearchHit::symbol).containsExactly("AAPL", "APLE");
    }

    @Test
    void getOrLoad_loaderFails_storesNothing() {
        UpstreamUnavailableException failure =
                new UpstreamUnavailableException(null);

        assertThatThrownBy(() -> cache.getOrLoad("apple", () -> {
            loaderCalls.incrementAndGet();
            throw failure;
        })).isSameAs(failure);

        List<SearchHit> retry = cache.getOrLoad("apple", loader(hit("AAPL")));
        assertThat(loaderCalls).hasValue(2);
        assertThat(retry).extracting(SearchHit::symbol).containsExactly("AAPL");
    }

    private Supplier<List<SearchHit>> loader(SearchHit... hits) {
        return () -> {
            loaderCalls.incrementAndGet();
            return List.of(hits);
        };
    }

    private static SearchHit hit(String symbol) {
        return new SearchHit(symbol, symbol + " Inc.", "NASDAQ", "XNAS", "USD", "Common Stock");
    }
}