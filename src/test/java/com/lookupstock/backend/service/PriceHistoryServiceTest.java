package com.lookupstock.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
import org.springframework.test.util.ReflectionTestUtils;

import com.lookupstock.backend.client.MarketDataProvider;
import com.lookupstock.backend.client.PricePoint;
import com.lookupstock.backend.exception.Messages;
import com.lookupstock.backend.exception.UpstreamUnavailableException;
import com.lookupstock.backend.model.Asset;

/** NF-07: Kursdaten jünger als 24 h kommen aus dem Cache, sonst neuer Abruf; bei Ausfall 503. */
class PriceHistoryServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-11T12:00:00Z");

    private MarketDataProvider provider;
    private PriceCache cache;
    private PriceHistoryService service;
    private Asset asset;

    private static final List<PricePoint> CACHED = List.of(
            new PricePoint(LocalDate.of(2026, 8, 1), new BigDecimal("100")),
            new PricePoint(LocalDate.of(2026, 9, 1), new BigDecimal("101")));

    @BeforeEach
    void setUp() {
        provider = mock(MarketDataProvider.class);
        cache = mock(PriceCache.class);
        service = new PriceHistoryService(provider, cache, Clock.fixed(NOW, ZoneOffset.UTC));
        asset = new Asset("EUNL", null, "MSCI World", "XETRA", "EUR", "ETF", "XETR");
        ReflectionTestUtils.setField(asset, "id", 7L);
    }

    @Test
    void cacheJuengerAls24hWirdOhneAbrufVerwendet() {
        when(cache.load(7L)).thenReturn(Optional.of(
                new PriceCache.CachedPrices(NOW.minus(Duration.ofHours(23)), CACHED)));

        assertThat(service.getMonthlyPrices(asset)).isEqualTo(CACHED);
        verify(provider, never()).getHistoricalPrices(anyString(), any(), anyString(), anyInt());
        verify(cache, never()).replace(any(), any(), any());
    }

    @Test
    void veralteterCacheUndAnbieterNichtErreichbarLiefert503() {
        when(cache.load(7L)).thenReturn(Optional.of(
                new PriceCache.CachedPrices(NOW.minus(Duration.ofHours(25)), CACHED)));
        when(provider.getHistoricalPrices("EUNL", "XETR", "1month", 120))
                .thenThrow(new UpstreamUnavailableException(Messages.UPSTREAM_UNAVAILABLE, null));

        assertThatThrownBy(() -> service.getMonthlyPrices(asset))
                .isInstanceOf(UpstreamUnavailableException.class);
        verify(cache, never()).replace(any(), any(), any());
    }

    @Test
    void ohneCacheWirdAbgerufenSortiertUndGespeichert() {
        when(cache.load(7L)).thenReturn(Optional.empty());
        // Twelve Data liefert die neuesten Werte zuerst
        when(provider.getHistoricalPrices("EUNL", "XETR", "1month", 120)).thenReturn(List.of(
                new PricePoint(LocalDate.of(2026, 9, 1), new BigDecimal("101")),
                new PricePoint(LocalDate.of(2026, 8, 1), new BigDecimal("100"))));

        List<PricePoint> result = service.getMonthlyPrices(asset);

        // auf 6 Nachkommastellen gerundet wie in price_history
        List<PricePoint> expected = List.of(
                new PricePoint(LocalDate.of(2026, 8, 1), new BigDecimal("100.000000")),
                new PricePoint(LocalDate.of(2026, 9, 1), new BigDecimal("101.000000")));
        assertThat(result).isEqualTo(expected);
        verify(cache).replace(7L, expected, NOW);
    }

    @Test
    void frischeUndGecachteKurseSindGleich() {
        // NF-08 (Docs #81): Twelve Data liefert mehr Nachkommastellen, als price_history speichert
        when(cache.load(7L)).thenReturn(Optional.empty());
        when(provider.getHistoricalPrices("EUNL", "XETR", "1month", 120)).thenReturn(List.of(
                new PricePoint(LocalDate.of(2026, 8, 1), new BigDecimal("100.12345650")),
                new PricePoint(LocalDate.of(2026, 9, 1), new BigDecimal("101.98765449"))));

        List<PricePoint> fresh = service.getMonthlyPrices(asset);

        // So liefert Postgres die Werte beim nächsten Lauf aus dem Cache (NUMERIC(19,6))
        List<PricePoint> cachedLater = List.of(
                new PricePoint(LocalDate.of(2026, 8, 1), new BigDecimal("100.123457")),
                new PricePoint(LocalDate.of(2026, 9, 1), new BigDecimal("101.987654")));
        assertThat(fresh).isEqualTo(cachedLater);

        when(cache.load(7L)).thenReturn(Optional.of(new PriceCache.CachedPrices(NOW, cachedLater)));
        assertThat(service.getMonthlyPrices(asset)).isEqualTo(fresh);
    }

    @Test
    void leereAntwortWirdNichtGespeichert() {
        when(cache.load(7L)).thenReturn(Optional.empty());
        when(provider.getHistoricalPrices("EUNL", "XETR", "1month", 120)).thenReturn(List.of());

        assertThat(service.getMonthlyPrices(asset)).isEmpty();
        verify(cache, never()).replace(any(), any(), any());
    }

    @Test
    void genau24hGiltAlsVeraltet() {
        assertThat(PriceHistoryService.isFresh(NOW.minus(Duration.ofHours(24)), NOW)).isFalse();
        assertThat(PriceHistoryService.isFresh(NOW.minus(Duration.ofHours(24)).plusSeconds(1), NOW)).isTrue();
    }
}
