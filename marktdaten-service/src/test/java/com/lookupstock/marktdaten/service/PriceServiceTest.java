package com.lookupstock.marktdaten.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import com.lookupstock.marktdaten.exception.UpstreamUnavailableException;
import com.lookupstock.marktdaten.model.PricePoint;
import com.lookupstock.marktdaten.twelvedata.MarketDataProvider;

class PriceServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-11T12:00:00Z");

    private static final List<PricePoint> CACHED = List.of(
            new PricePoint(LocalDate.of(2026, 8, 1), new BigDecimal("100")),
            new PricePoint(LocalDate.of(2026, 9, 1), new BigDecimal("101")));

    private MarketDataProvider provider;
    private PriceCacheRepository cache;
    private PriceService service;

    @BeforeEach
    void setUp() {
        provider = mock(MarketDataProvider.class);
        cache = mock(PriceCacheRepository.class);
        service = new PriceService(provider, cache, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void cacheYoungerThan24hIsUsedWithoutCallingProvider() {
        when(cache.load("URTH", "ARCX")).thenReturn(Optional.of(
                new PriceCacheRepository.CachedPrices(NOW.minus(Duration.ofHours(23)), CACHED)));

        assertThat(service.getPrices("URTH", "ARCX", "1month", 120)).isEqualTo(CACHED);
        verify(provider, never()).getHistoricalPrices(anyString(), any(), anyString(), anyInt());
        verify(cache, never()).replace(any(), any(), any(), any());
    }

    @Test
    void staleCacheAndProviderUnavailableReturns503() {
        when(cache.load("URTH", "ARCX")).thenReturn(Optional.of(
                new PriceCacheRepository.CachedPrices(NOW.minus(Duration.ofHours(25)), CACHED)));
        when(provider.getHistoricalPrices("URTH", "ARCX", "1month", 120))
                .thenThrow(new UpstreamUnavailableException(null));

        assertThatThrownBy(() -> service.getPrices("URTH", "ARCX", "1month", 120))
                .isInstanceOf(UpstreamUnavailableException.class);
        verify(cache, never()).replace(any(), any(), any(), any());
    }

    @Test
    void withoutCachePricesAreFetchedSortedRoundedAndStored() {
        when(cache.load("URTH", "ARCX")).thenReturn(Optional.empty());
        when(provider.getHistoricalPrices("URTH", "ARCX", "1month", 120)).thenReturn(List.of(
                new PricePoint(LocalDate.of(2026, 10, 1), new BigDecimal("102")),
                new PricePoint(LocalDate.of(2026, 9, 1), new BigDecimal("101.98765449")),
                new PricePoint(LocalDate.of(2026, 8, 1), new BigDecimal("100.12345650"))));

        List<PricePoint> result = service.getPrices("URTH", "ARCX", "1month", 120);

        List<PricePoint> expected = List.of(
                new PricePoint(LocalDate.of(2026, 8, 1), new BigDecimal("100.123457")),
                new PricePoint(LocalDate.of(2026, 9, 1), new BigDecimal("101.987654")),
                new PricePoint(LocalDate.of(2026, 10, 1), new BigDecimal("102.000000")));
        assertThat(result).isEqualTo(expected);
        verify(cache).replace("URTH", "ARCX", expected, NOW);
    }

    @Test
    void emptyAnswerIsNotStored() {
        when(cache.load("UNKNOWN", null)).thenReturn(Optional.empty());
        when(provider.getHistoricalPrices("UNKNOWN", null, "1month", 120)).thenReturn(List.of());

        assertThat(service.getPrices("UNKNOWN", null, "1month", 120)).isEmpty();
        verify(cache, never()).replace(any(), any(), any(), any());
    }

    @Test
    void failedCacheWriteStillReturnsPrices() {
        when(cache.load("URTH", "ARCX")).thenReturn(Optional.empty());
        when(provider.getHistoricalPrices("URTH", "ARCX", "1month", 120)).thenReturn(CACHED);
        doThrow(new DuplicateKeyException("gleichzeitiger Abruf")).when(cache).replace(any(), any(), any(), any());

        assertThat(service.getPrices("URTH", "ARCX", "1month", 120)).hasSize(2);
    }

    @Test
    void otherIntervalsAreNotCached() {
        when(provider.getHistoricalPrices("URTH", null, "1day", 30)).thenReturn(CACHED);

        assertThat(service.getPrices("URTH", null, "1day", 30)).isEqualTo(CACHED);
        verifyNoInteractions(cache);
    }

    @Test
    void exactly24HoursIsStale() {
        assertThat(PriceService.isFresh(NOW.minus(Duration.ofHours(24)), NOW)).isFalse();
        assertThat(PriceService.isFresh(NOW.minus(Duration.ofHours(24)).plusSeconds(1), NOW)).isTrue();
    }
}
