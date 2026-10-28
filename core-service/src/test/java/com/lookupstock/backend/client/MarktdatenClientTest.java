package com.lookupstock.backend.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.lookupstock.backend.exception.Messages;
import com.lookupstock.backend.exception.UpstreamUnavailableException;

/** BE #106: Aufrufe an den marktdaten-service mit internem Schlüssel; jeder Fehler wird 503 (NF-07). */
class MarktdatenClientTest {

    private static final String BASE_URL = "http://marktdaten.test";
    private static final String KEY = "geheimer-test-schluessel";

    private MockRestServiceServer server;
    private MarktdatenClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new MarktdatenClient(builder, BASE_URL, KEY);
    }

    @Test
    void searchSendsKeyAndReturnsHits() {
        server.expect(requestTo(startsWith(BASE_URL + "/internal/search")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("q", "msci"))
                .andExpect(header(MarktdatenClient.API_KEY_HEADER, KEY))
                .andRespond(withSuccess("""
                        [{"symbol":"URTH","name":"iShares MSCI World ETF","exchange":"NYSE",
                          "micCode":"ARCX","currency":"USD","type":"ETF"}]""", MediaType.APPLICATION_JSON));

        List<SearchHit> hits = client.search("msci");

        assertThat(hits).containsExactly(
                new SearchHit("URTH", "iShares MSCI World ETF", "NYSE", "ARCX", "USD", "ETF"));
        server.verify();
    }

    @Test
    void pricesSendSymbolMicIntervalAndParseDates() {
        server.expect(requestTo(startsWith(BASE_URL + "/internal/prices")))
                .andExpect(queryParam("symbol", "URTH"))
                .andExpect(queryParam("mic", "ARCX"))
                .andExpect(queryParam("interval", "1month"))
                .andExpect(queryParam("outputsize", "120"))
                .andExpect(header(MarktdatenClient.API_KEY_HEADER, KEY))
                .andRespond(withSuccess("""
                        [{"date":"2026-08-01","close":100.123457},{"date":"2026-09-01","close":101.5}]""",
                        MediaType.APPLICATION_JSON));

        List<PricePoint> prices = client.getHistoricalPrices("URTH", "ARCX", "1month", 120);

        assertThat(prices).extracting(PricePoint::date)
                .containsExactly(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 1));
        assertThat(prices.getFirst().close()).isEqualByComparingTo(new BigDecimal("100.123457"));
        server.verify();
    }

    @Test
    void pricesWithoutMicOmitParameter() {
        server.expect(requestTo(not(containsString("mic="))))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThat(client.getHistoricalPrices("URTH", "1day", 30)).isEmpty();
        server.verify();
    }

    @Test
    void serviceUnavailableBecomesUpstreamUnavailable() {
        server.expect(requestTo(startsWith(BASE_URL + "/internal/prices")))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> client.getHistoricalPrices("URTH", "ARCX", "1month", 120))
                .isInstanceOf(UpstreamUnavailableException.class)
                .hasMessage(Messages.UPSTREAM_UNAVAILABLE);
    }

    @Test
    void wrongInternalKeyBecomesUpstreamUnavailable() {
        server.expect(requestTo(startsWith(BASE_URL + "/internal/search")))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> client.search("msci"))
                .isInstanceOf(UpstreamUnavailableException.class)
                .hasMessage(Messages.UPSTREAM_UNAVAILABLE);
    }

    @Test
    void unreachableServiceBecomesUpstreamUnavailable() {
        // z. B. Timeout oder marktdaten-service nicht gestartet
        server.expect(requestTo(startsWith(BASE_URL + "/internal/search")))
                .andRespond(request -> {
                    throw new IOException("Connection refused");
                });

        assertThatThrownBy(() -> client.search("msci"))
                .isInstanceOf(UpstreamUnavailableException.class)
                .hasMessage(Messages.UPSTREAM_UNAVAILABLE);
    }
}
