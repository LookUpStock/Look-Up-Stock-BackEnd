package com.lookupstock.backend.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.lookupstock.backend.dto.AssetResponse;
import com.lookupstock.backend.dto.AssetPricesResponse;
import com.lookupstock.backend.dto.AssetStatsResponse;
import com.lookupstock.backend.dto.SearchResponse;
import com.lookupstock.backend.exception.ValidationException;
import com.lookupstock.backend.service.AssetService;
import com.lookupstock.backend.service.AssetStatisticsService;

@RestController
@RequestMapping("/api/v1/assets")
public class AssetController {

    private final AssetService assetService;
    private final AssetStatisticsService assetStatisticsService;

    public AssetController(AssetService assetService, AssetStatisticsService assetStatisticsService) {
        this.assetService = assetService;
        this.assetStatisticsService = assetStatisticsService;
    }

    @GetMapping("/search")
    public SearchResponse search(@RequestParam(required = false) String q,
                                 @RequestParam(required = false) Integer limit) {
        List<AssetResponse> results = assetService.search(q, limit).stream()
                .map(AssetResponse::from)
                .toList();
        return new SearchResponse(q, results);
    }

    @GetMapping("/{symbol}/stats")
    public AssetStatsResponse stats(@PathVariable String symbol,
            @RequestParam(defaultValue = "1day") String interval,
            @RequestParam(defaultValue = "30", required = false) Integer outputSize) {
        if (symbol == null || symbol.isBlank()) {
            throw new ValidationException("Bitte ein Symbol angeben.", List.of(
                    new ValidationException.FieldError("symbol", "Bitte ein Symbol angeben.")));
        }
        return assetStatisticsService.calculate(symbol.trim(), interval, outputSize == null ? 30 : outputSize);
    }

    @GetMapping("/{symbol}/prices")
    public AssetPricesResponse prices(@PathVariable String symbol,
            @RequestParam(defaultValue = "1day") String interval,
            @RequestParam(defaultValue = "30", required = false) Integer outputSize) {
        var prices = assetService.getHistoricalPrices(symbol, interval, outputSize);
        return new AssetPricesResponse(symbol.trim(), interval, prices.size(), prices);
    }
}
