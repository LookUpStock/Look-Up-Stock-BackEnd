package com.lookupstock.backend.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.lookupstock.backend.exception.UpstreamUnavailableException;
import com.lookupstock.backend.model.Asset;
import com.lookupstock.backend.service.PriceHistoryService;

/** NF-12: Die Testattrappe liefert die Testdaten aus Abschnitt 6 des Testkonzepts. */
class MockMarketDataProviderTest {

    private static final String MONTHLY = "1month";

    private final MockMarketDataProvider provider = new MockMarketDataProvider();

    @Test
    void longSeriesHas120MonthsNewestFirst() {
        List<PricePoint> prices = provider.getHistoricalPrices(MockMarketDataProvider.WORLD_ETF, MONTHLY, 120);

        assertThat(prices).hasSize(MockMarketDataProvider.LONG_HISTORY_MONTHS);
        assertThat(prices.getFirst().date()).isEqualTo(MockMarketDataProvider.DEFAULT_LAST_MONTH);
        // Wie Twelve Data: neueste Werte zuerst
        assertThat(prices).isSortedAccordingTo(Comparator.comparing(PricePoint::date).reversed());
    }

    @Test
    void outputSizeLimitsResult() {
        assertThat(provider.getHistoricalPrices(MockMarketDataProvider.WORLD_ETF, MONTHLY, 12)).hasSize(12);
    }

    @Test
    void boundarySeriesHave35And36Months() {
        // Grenzwerte E8: 35 Monatswerte reichen nicht, 36 reichen
        assertThat(provider.getHistoricalPrices(MockMarketDataProvider.SHORT_HISTORY_A, MONTHLY, 120)).hasSize(35);
        assertThat(provider.getHistoricalPrices(MockMarketDataProvider.SHORT_HISTORY_B, MONTHLY, 120)).hasSize(35);
        assertThat(provider.getHistoricalPrices(MockMarketDataProvider.EXACT_36_MONTHS, MONTHLY, 120)).hasSize(36);
    }

    @Test
    void searchForMsciReturnsMoreThan25Hits() {
        // Grundlage für die Grenzwerte von limit (AT-02-05)
        assertThat(provider.search("msci")).hasSizeGreaterThan(25);
    }

    @Test
    void unknownSymbolReturnsEmptyList() {
        assertThat(provider.getHistoricalPrices("UNKNOWN", MONTHLY, 120)).isEmpty();
    }

    @Test
    void sameInputGivesSameSeries() {
        // NF-08: ohne Zufall bei jedem Lauf identisch
        assertThat(new MockMarketDataProvider().getHistoricalPrices(MockMarketDataProvider.WORLD_ETF, MONTHLY, 120))
                .isEqualTo(provider.getHistoricalPrices(MockMarketDataProvider.WORLD_ETF, MONTHLY, 120));
    }

    @Test
    void unavailableProviderThrowsUpstreamUnavailable() {
        // NF-07: simulierter Ausfall der Finanzdaten-API
        provider.setUnavailable(true);

        assertThatThrownBy(() -> provider.search("msci"))
                .isInstanceOf(UpstreamUnavailableException.class);
        assertThatThrownBy(() -> provider.getHistoricalPrices(MockMarketDataProvider.WORLD_ETF, MONTHLY, 120))
                .isInstanceOf(UpstreamUnavailableException.class);
    }

    @Test
    void priceHistoryServiceSortsMockDataAscending() {
        // Zusammenspiel mit dem echten Service (der Kurs-Cache liegt seit BE #106 im marktdaten-service)
        Clock clock = Clock.fixed(Instant.parse("2026-10-11T12:00:00Z"), ZoneOffset.UTC);
        PriceHistoryService service = new PriceHistoryService(provider, clock);
        Asset asset = new Asset("EUNL", null, "MSCI World", "XETRA", "EUR", "ETF", "XETR");
        ReflectionTestUtils.setField(asset, "id", 1L);

        List<PricePoint> prices = service.getMonthlyPrices(asset);

        // Laufender Monat (Oktober) liegt nicht in der Reihe, daher bleiben alle 120 Werte (E22)
        assertThat(prices).hasSize(120).isSortedAccordingTo(Comparator.comparing(PricePoint::date));
        assertThat(provider.getPriceRequestCount()).isEqualTo(1);
    }
}