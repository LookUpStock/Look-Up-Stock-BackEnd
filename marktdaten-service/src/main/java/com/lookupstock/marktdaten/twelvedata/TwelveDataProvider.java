package com.lookupstock.marktdaten.twelvedata;

import java.time.Duration;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.lookupstock.marktdaten.exception.UpstreamUnavailableException;
import com.lookupstock.marktdaten.model.PricePoint;
import com.lookupstock.marktdaten.model.SearchHit;

@Component
public class TwelveDataProvider implements MarketDataProvider {

    private static final Logger log = LoggerFactory.getLogger(TwelveDataProvider.class);

    private final RestClient restClient;
    private final String apiKey;

    public TwelveDataProvider(@Value("${twelvedata.base-url}") String baseUrl,
            @Value("${twelvedata.api-key}") String apiKey) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(3));
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
        this.apiKey = apiKey;
    }

    @Override
    public List<SearchHit> search(String query) {
        TwelveDataSearchResponse response;
        try {
            response = restClient.get()
                    .uri(b -> b.path("/symbol_search").queryParam("symbol", query).queryParam("apikey", apiKey).build())
                    .retrieve()
                    .body(TwelveDataSearchResponse.class);
        } catch (RestClientResponseException e) {
            return handleHttpError("symbol_search", e);
        } catch (RestClientException e) {
            throw unreachable("symbol_search", e);
        }

        if (response == null) {
            throw unavailable("symbol_search", null, "leere Antwort");
        }
        if (!"ok".equals(response.status())) {
            return handleError("symbol_search", response.code(), response.message());
        }
        if (response.data() == null) {
            return List.of();
        }
        return response.data().stream()
                .map(s -> new SearchHit(s.symbol(), s.instrumentName(), s.exchange(),
                        s.micCode(), s.currency(), s.instrumentType()))
                .toList();
    }

    @Override
    public List<PricePoint> getHistoricalPrices(String symbol, String micCode, String interval, int outputSize) {
        TwelveDataTimeSeriesResponse response;
        try {
            response = restClient.get()
                    .uri(b -> {
                        b.path("/time_series")
                                .queryParam("symbol", symbol)
                                .queryParam("interval", interval)
                                .queryParam("outputsize", outputSize)
                                .queryParam("apikey", apiKey);
                        if (micCode != null && !micCode.isBlank()) {
                            b.queryParam("mic_code", micCode);
                        }
                        return b.build();
                    })
                    .retrieve()
                    .body(TwelveDataTimeSeriesResponse.class);
        } catch (RestClientResponseException e) {
            return handleHttpError("time_series " + symbol, e);
        } catch (RestClientException e) {
            throw unreachable("time_series " + symbol, e);
        }

        if (response == null) {
            throw unavailable("time_series " + symbol, null, "leere Antwort");
        }
        if (!"ok".equals(response.status())) {
            return handleError("time_series " + symbol, response.code(), response.message());
        }
        if (response.values() == null) {
            return List.of();
        }
        return response.values().stream()
                .filter(price -> price.close() != null)
                .map(TwelveDataPrice::toPricePoint)
                .toList();
    }

    // Unknown symbol or exchange not in the plan: empty list, Twelve Data itself is reachable (no 503).
    private <T> List<T> handleError(String operation, Integer code, String message) {
        TwelveDataError error = TwelveDataError.fromCode(code);
        if (error == TwelveDataError.NOT_FOUND || error == TwelveDataError.PLAN_RESTRICTED) {
            log.warn("Twelve Data ({}): keine Kurse verfügbar (Code {}, {}) – {}", operation, code, error, message);
            return List.of();
        }
        throw unavailable(operation, code, message);
    }

    private <T> List<T> handleHttpError(String operation, RestClientResponseException e) {
        return handleError(operation, e.getStatusCode().value(), e.getStatusText());
    }

    private UpstreamUnavailableException unavailable(String operation, Integer code, String message) {
        switch (TwelveDataError.fromCode(code)) {
            case INVALID_KEY -> log.error("Twelve Data ({}): API-Key fehlt oder ist ungültig (Code {}) – {}",
                    operation, code, message);
            case RATE_LIMIT -> log.warn("Twelve Data ({}): API-Limit erreicht (Code 429) – {}", operation, message);
            default -> log.warn("Twelve Data ({}): Fehler {} – {}", operation, code, message);
        }
        return new UpstreamUnavailableException(null);
    }

    private UpstreamUnavailableException unreachable(String operation, RestClientException e) {
        log.warn("Twelve Data ({}): nicht erreichbar – {}", operation, e.getMessage());
        return new UpstreamUnavailableException(e);
    }
}
