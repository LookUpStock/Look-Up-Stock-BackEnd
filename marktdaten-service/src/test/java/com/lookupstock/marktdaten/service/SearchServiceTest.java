package com.lookupstock.marktdaten.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.lookupstock.marktdaten.exception.UpstreamUnavailableException;
import com.lookupstock.marktdaten.model.SearchHit;
import com.lookupstock.marktdaten.twelvedata.MarketDataProvider;

/** NF-02: Suche mit Cache; die Fälle stammen aus dem früheren AssetServiceTest des core-service. */
class SearchServiceTest {

    private MarketDataProvider provider;
    private SearchService service;

    @BeforeEach
    void setUp() {
        provider = mock(MarketDataProvider.class);
        SearchCache cache = new SearchCache(Clock.fixed(Instant.parse("2026-10-17T12:00:00Z"), ZoneOffset.UTC));
        service = new SearchService(provider, cache);
    }

    @Test
    void trimsQueryBeforeCallingProvider() {
        service.search("  apple  ");
        verify(provider).search("apple");
    }

    @Test
    void sameQueryTwiceCallsProviderOnlyOnce() {
        when(provider.search("apple")).thenReturn(List.of(hit("AAPL")));

        service.search("apple");
        List<SearchHit> second = service.search("Apple ");

        verify(provider, times(1)).search(any());
        assertThat(second).extracting(SearchHit::symbol).containsExactly("AAPL");
    }

    @Test
    void providerUnavailableIsNotCachedAndRetriedNextTime() {
        UpstreamUnavailableException failure = new UpstreamUnavailableException(null);
        when(provider.search("apple")).thenThrow(failure).thenReturn(List.of(hit("AAPL")));

        assertThatThrownBy(() -> service.search("apple")).isSameAs(failure);
        List<SearchHit> second = service.search("apple");

        verify(provider, times(2)).search("apple");
        assertThat(second).extracting(SearchHit::symbol).containsExactly("AAPL");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void blankQueryIsRejectedWithoutCallingProvider(String query) {
        assertThatThrownBy(() -> service.search(query)).isInstanceOf(IllegalArgumentException.class);
        verify(provider, never()).search(any());
    }

    private static SearchHit hit(String symbol) {
        return new SearchHit(symbol, symbol + " Inc.", "NASDAQ", "XNAS", "USD", "Common Stock");
    }
}
