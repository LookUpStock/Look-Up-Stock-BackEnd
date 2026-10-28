package com.lookupstock.marktdaten.web;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.lookupstock.marktdaten.model.PricePoint;
import com.lookupstock.marktdaten.model.SearchHit;
import com.lookupstock.marktdaten.service.PriceService;
import com.lookupstock.marktdaten.service.SearchService;

/**
 * Interne Endpunkte, nur für den core-service (ADR-07); geschützt durch den InternalApiKeyFilter.
 * Symbol und Börse kommen als Query-Parameter, damit auch Symbole mit „/“ (z. B. BTC/USD) funktionieren.
 */
@RestController
@RequestMapping("/internal")
public class InternalController {

    private static final int MAX_OUTPUT_SIZE = 5000;

    private final SearchService searchService;
    private final PriceService priceService;

    public InternalController(SearchService searchService, PriceService priceService) {
        this.searchService = searchService;
        this.priceService = priceService;
    }

    /** GET /internal/search?q=msci – Treffer in der Reihenfolge des Anbieters */
    @GetMapping("/search")
    public List<SearchHit> search(@RequestParam(required = false) String q) {
        return searchService.search(q);
    }

    /** GET /internal/prices?symbol=URTH&mic=ARCX&interval=1month&outputsize=120 – Monatswerte aus dem Cache */
    @GetMapping("/prices")
    public List<PricePoint> prices(@RequestParam(required = false) String symbol,
                                   @RequestParam(required = false) String mic,
                                   @RequestParam(defaultValue = "1month") String interval,
                                   @RequestParam(defaultValue = "120") int outputsize) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("Parameter symbol fehlt.");
        }
        if (outputsize < 1 || outputsize > MAX_OUTPUT_SIZE) {
            throw new IllegalArgumentException("outputsize muss zwischen 1 und 5000 liegen.");
        }
        String micCode = mic == null || mic.isBlank() ? null : mic.trim();
        return priceService.getPrices(symbol.trim(), micCode, interval.trim(), outputsize);
    }
}
