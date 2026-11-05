package com.lookupstock.backend.client;

import java.time.Duration;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.lookupstock.backend.exception.Messages;
import com.lookupstock.backend.exception.UpstreamUnavailableException;

@Component
public class MarktdatenClient implements MarketDataProvider {

    public static final String API_KEY_HEADER = "X-Internal-Api-Key";

    private static final Logger log = LoggerFactory.getLogger(MarktdatenClient.class);
    private static final ParameterizedTypeReference<List<SearchHit>> SEARCH_HITS = new ParameterizedTypeReference<>() {
    };
    private static final ParameterizedTypeReference<List<PricePoint>> PRICE_POINTS = new ParameterizedTypeReference<>() {
    };

    private final RestClient restClient;

    @Autowired
    public MarktdatenClient(@Value("${marktdaten.base-url}") String baseUrl,
                            @Value("${marktdaten.internal-api-key}") String internalApiKey) {
        this(RestClient.builder().requestFactory(timeouts()), baseUrl, internalApiKey);
    }

    MarktdatenClient(RestClient.Builder builder, String baseUrl, String internalApiKey) {
        this.restClient = builder
                .baseUrl(baseUrl)
                .defaultHeader(API_KEY_HEADER, internalApiKey)
                .build();
    }

    // 5 s timeout; the market data service itself waits at most 3 s for Twelve Data.
    private static SimpleClientHttpRequestFactory timeouts() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(5));
        return factory;
    }

    @Override
    public List<SearchHit> search(String query) {
        try {
            List<SearchHit> hits = restClient.get()
                    .uri(b -> b.path("/internal/search").queryParam("q", query).build())
                    .retrieve()
                    .body(SEARCH_HITS);
            return hits == null ? List.of() : hits;
        } catch (RestClientResponseException e) {
            throw failed("search", e);
        } catch (RestClientException e) {
            throw unreachable("search", e);
        }
    }

    @Override
    public List<PricePoint> getHistoricalPrices(String symbol, String micCode, String interval, int outputSize) {
        try {
            List<PricePoint> prices = restClient.get()
                    .uri(b -> {
                        b.path("/internal/prices")
                                .queryParam("symbol", symbol)
                                .queryParam("interval", interval)
                                .queryParam("outputsize", outputSize);
                        if (micCode != null && !micCode.isBlank()) {
                            b.queryParam("mic", micCode);
                        }
                        return b.build();
                    })
                    .retrieve()
                    .body(PRICE_POINTS);
            return prices == null ? List.of() : prices;
        } catch (RestClientResponseException e) {
            throw failed("prices " + symbol, e);
        } catch (RestClientException e) {
            throw unreachable("prices " + symbol, e);
        }
    }

    private UpstreamUnavailableException failed(String operation, RestClientResponseException e) {
        int status = e.getStatusCode().value();
        if (status == 401) {
            log.error("marktdaten-service ({}): interner Schlüssel abgelehnt – INTERNAL_API_KEY in beiden Apps prüfen",
                    operation);
        } else {
            log.warn("marktdaten-service ({}): Fehler {}", operation, status);
        }
        return new UpstreamUnavailableException(Messages.UPSTREAM_UNAVAILABLE, e);
    }

    private UpstreamUnavailableException unreachable(String operation, RestClientException e) {
        log.warn("marktdaten-service ({}): nicht erreichbar – {}", operation, e.getMessage());
        return new UpstreamUnavailableException(Messages.UPSTREAM_UNAVAILABLE, e);
    }
}
