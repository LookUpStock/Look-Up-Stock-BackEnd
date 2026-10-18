package com.lookupstock.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.lookupstock.backend.client.PricePoint;
import com.lookupstock.backend.model.Asset;
import com.lookupstock.backend.repository.AssetRepository;

class AssetStatisticsServiceTest {

    private PriceHistoryService priceHistoryService;
    private AssetRepository assetRepository;
    private AssetStatisticsService service;
    private Asset asset;

    @BeforeEach
    void setUp() {
        priceHistoryService = org.mockito.Mockito.mock(PriceHistoryService.class);
        assetRepository = org.mockito.Mockito.mock(AssetRepository.class);
        service = new AssetStatisticsService(priceHistoryService, assetRepository);
        asset = new Asset("EUNL", null, "MSCI World", "XETRA", "EUR", "ETF", "XETR");
    }

    @Test
    void verwendetGespeichertesAssetMitMicCode() {
        List<PricePoint> prices = List.of(
                new PricePoint(LocalDate.of(2026, 8, 1), new BigDecimal("100")),
                new PricePoint(LocalDate.of(2026, 9, 1), new BigDecimal("110")));
        when(assetRepository.findFirstBySymbolIgnoreCaseOrderByIdAsc("EUNL")).thenReturn(Optional.of(asset));
        when(priceHistoryService.getPrices(asset, "1day", 30)).thenReturn(prices);

        var result = service.calculate(" EUNL ", "1day", 30);

        assertThat(result.symbol()).isEqualTo("EUNL");
        assertThat(result.dataPoints()).isEqualTo(2);
        verify(priceHistoryService).getPrices(asset, "1day", 30);
    }

    @Test
    void unbekanntesAssetWirdOhneRepositoryEintragAbgefragt() {
        when(assetRepository.findFirstBySymbolIgnoreCaseOrderByIdAsc("AAPL")).thenReturn(Optional.empty());
        when(priceHistoryService.getPrices(org.mockito.ArgumentMatchers.any(Asset.class), org.mockito.ArgumentMatchers.eq("1day"), org.mockito.ArgumentMatchers.eq(30)))
                .thenReturn(List.of(
                        new PricePoint(LocalDate.of(2026, 8, 1), new BigDecimal("100")),
                        new PricePoint(LocalDate.of(2026, 9, 1), new BigDecimal("110"))));

        assertThat(service.calculate("AAPL", "1day", 30).dataPoints()).isEqualTo(2);
    }
}
