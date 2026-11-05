package com.lookupstock.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.lookupstock.backend.client.MarketDataProvider;
import com.lookupstock.backend.client.PricePoint;
import com.lookupstock.backend.exception.Messages;
import com.lookupstock.backend.exception.UpstreamUnavailableException;
import com.lookupstock.backend.model.Asset;

class PriceHistoryServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-11T12:00:00Z");

    private MarketDataProvider provider;
    private PriceHistoryService service;
    private Asset asset;

    @BeforeEach
    void setUp() {
        provider = mock(MarketDataProvider.class);
        service = new PriceHistoryService(provider, Clock.fixed(NOW, ZoneOffset.UTC));
        asset = new Asset("URTH", null, "iShares MSCI World ETF", "NYSE", "USD", "ETF", "ARCX");
        ReflectionTestUtils.setField(asset, "id", 7L);
    }

    @Test
    void kurseWerdenMitSymbolUndMicAbgerufenUndAufsteigendSortiert() {
        when(provider.getHistoricalPrices("URTH", "ARCX", "1month", 120)).thenReturn(List.of(
                new PricePoint(LocalDate.of(2026, 9, 1), new BigDecimal("101")),
                new PricePoint(LocalDate.of(2026, 8, 1), new BigDecimal("100"))));

        assertThat(service.getMonthlyPrices(asset)).containsExactly(
                new PricePoint(LocalDate.of(2026, 8, 1), new BigDecimal("100.000000")),
                new PricePoint(LocalDate.of(2026, 9, 1), new BigDecimal("101.000000")));
    }

    @Test
    void kurseWerdenAufSechsNachkommastellenGerundet() {
        when(provider.getHistoricalPrices("URTH", "ARCX", "1month", 120)).thenReturn(List.of(
                new PricePoint(LocalDate.of(2026, 8, 1), new BigDecimal("100.12345650")),
                new PricePoint(LocalDate.of(2026, 9, 1), new BigDecimal("101.98765449"))));

        assertThat(service.getMonthlyPrices(asset)).extracting(PricePoint::close)
                .containsExactly(new BigDecimal("100.123457"), new BigDecimal("101.987654"));
    }

    @Test
    void laufenderMonatWirdAusgeschlossen() {
        when(provider.getHistoricalPrices("URTH", "ARCX", "1month", 120)).thenReturn(withOctober());

        assertThat(service.getMonthlyPrices(asset)).extracting(PricePoint::date)
                .containsExactly(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 1));
    }

    @Test
    void nachDemMonatswechselZaehltDerAbgeschlosseneMonat() {
        PriceHistoryService november = new PriceHistoryService(provider,
                Clock.fixed(Instant.parse("2026-11-01T00:00:00Z"), ZoneOffset.UTC));
        when(provider.getHistoricalPrices("URTH", "ARCX", "1month", 120)).thenReturn(withOctober());

        assertThat(november.getMonthlyPrices(asset)).hasSize(3);
    }

    @Test
    void letzterTagDesMonatsGehoertNochZumLaufendenMonat() {
        PriceHistoryService octoberEnd = new PriceHistoryService(provider,
                Clock.fixed(Instant.parse("2026-10-31T23:59:59Z"), ZoneOffset.UTC));
        when(provider.getHistoricalPrices("URTH", "ARCX", "1month", 120)).thenReturn(withOctober());

        assertThat(octoberEnd.getMonthlyPrices(asset)).hasSize(2);
    }

    @Test
    void leereAntwortErgibtLeereListe() {
        when(provider.getHistoricalPrices("URTH", "ARCX", "1month", 120)).thenReturn(List.of());

        assertThat(service.getMonthlyPrices(asset)).isEmpty();
    }

    @Test
    void anbieterNichtErreichbarLiefert503() {
        when(provider.getHistoricalPrices("URTH", "ARCX", "1month", 120))
                .thenThrow(new UpstreamUnavailableException(Messages.UPSTREAM_UNAVAILABLE, null));

        assertThatThrownBy(() -> service.getMonthlyPrices(asset)).isInstanceOf(UpstreamUnavailableException.class);
    }

    private static List<PricePoint> withOctober() {
        return List.of(
                new PricePoint(LocalDate.of(2026, 10, 1), new BigDecimal("102")),
                new PricePoint(LocalDate.of(2026, 9, 1), new BigDecimal("101")),
                new PricePoint(LocalDate.of(2026, 8, 1), new BigDecimal("100")));
    }
}
