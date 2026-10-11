package com.lookupstock.backend.client;

import java.time.Duration;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.lookupstock.backend.exception.Messages;
import com.lookupstock.backend.exception.UpstreamUnavailableException;

@Component
public class TwelveDataProvider implements MarketDataProvider {

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
        try {
            TwelveDataSearchResponse response = restClient.get()
                    .uri(b -> b.path("/symbol_search").queryParam("symbol", query).queryParam("apikey", apiKey).build())
                    .retrieve()
                    .body(TwelveDataSearchResponse.class);
            if (response == null || !"ok".equals(response.status()) || response.data() == null) {
                throw new UpstreamUnavailableException(Messages.UPSTREAM_UNAVAILABLE, null);
            }
            return response.data().stream()
                    .map(s -> new SearchHit(s.symbol(), s.instrumentName(), s.exchange(),
                    s.micCode(), s.currency(), s.instrumentType()))
                    .toList();
        } catch (RestClientException e) {
            throw new UpstreamUnavailableException(Messages.UPSTREAM_UNAVAILABLE, e);
        }
    }

    @Override
    public List<PricePoint> getHistoricalPrices(String symbol, String interval, int outputSize) {
        try {
            TwelveDataTimeSeriesResponse response = restClient.get()
                    .uri(b -> b.path("/time_series")
                            .queryParam("symbol", symbol)
                            .queryParam("interval", interval)
                            .queryParam("outputsize", outputSize)
                            .queryParam("apikey", apiKey)
                            .build())
                    .retrieve()
                    .body(TwelveDataTimeSeriesResponse.class);
            if (response == null || !"ok".equals(response.status()) || response.values() == null || response.values().isEmpty()) {
                throw new UpstreamUnavailableException("Kursdaten sind gerade nicht verfügbar.", null);
            }
            return response.values().stream()
                    .filter(price -> price.close() != null)
                    .map(TwelveDataPrice::toPricePoint)
                    .toList();
        } catch (RestClientException e) {
            throw new UpstreamUnavailableException("Course data is currently unavailable.", e);
        }
    }
}
