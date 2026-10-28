package com.lookupstock.backend.service;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lookupstock.backend.client.MarketDataProvider;
import com.lookupstock.backend.client.SearchHit;
import com.lookupstock.backend.exception.Messages;
import com.lookupstock.backend.exception.UpstreamUnavailableException;
import com.lookupstock.backend.exception.ValidationException;
import com.lookupstock.backend.model.Asset;
import com.lookupstock.backend.repository.AssetRepository;



class AssetServiceTest {

    // Externe Kursdaten-API und Datenbank werden gemockt
    private MarketDataProvider marketDataProvider;
    private AssetRepository assetRepository;
    private SearchCache searchCache;

    // Der echte Service, der getestet wird
    private AssetService service;

    // Läuft vor JEDEM Test: frische Mocks
    @BeforeEach
    void setUp() {
        marketDataProvider = mock(MarketDataProvider.class);
        assetRepository = mock(AssetRepository.class);
        // Echter Cache mit fester Uhr, damit jeder Test mit leerem Cache startet
        searchCache = new SearchCache(Clock.fixed(Instant.parse("2026-10-17T12:00:00Z"), ZoneOffset.UTC));
        service = new AssetService(marketDataProvider, assetRepository, searchCache);

        // save() gibt die übergebene Anlage unverändert zurück
        when(assetRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    // ---------- Erfolgsfall ----------

    @Test
    void search_validQuery_returnsOneAssetPerHit() {
        // Arrange: API liefert zwei Treffer
        when(marketDataProvider.search("apple")).thenReturn(List.of(hit("AAPL"), hit("APLE")));

        // Act
        List<Asset> result = service.search("apple", null);

        // Assert: pro Treffer eine Anlage, Reihenfolge bleibt erhalten
        assertThat(result).extracting(Asset::getSymbol).containsExactly("AAPL", "APLE");
    }

    @Test
    void search_trimsQueryBeforeCallingProvider() {
        // Act: Suchbegriff mit Leerzeichen
        service.search("  apple  ", null);

        // Assert: An die API geht der bereinigte Suchbegriff
        verify(marketDataProvider).search("apple");
    }

    // ---------- Anlagen anlegen oder wiederverwenden ----------

    @Test
    void search_newHit_isSavedAsAssetWithHitData() {
        // Arrange: Treffer, den es in der Datenbank noch nicht gibt
        when(marketDataProvider.search("apple")).thenReturn(List.of(
                new SearchHit("AAPL", "Apple Inc.", "NASDAQ", "XNGS", "USD", "Common Stock")));

        // Act
        service.search("apple", null);

        // Assert: Die gespeicherte Anlage enthält alle Daten aus dem Treffer
        ArgumentCaptor<Asset> captor = ArgumentCaptor.forClass(Asset.class);
        verify(assetRepository).save(captor.capture());
        Asset saved = captor.getValue();

        assertThat(saved.getSymbol()).isEqualTo("AAPL");
        assertThat(saved.getName()).isEqualTo("Apple Inc.");
        assertThat(saved.getExchange()).isEqualTo("NASDAQ");
        assertThat(saved.getMicCode()).isEqualTo("XNGS");
        assertThat(saved.getCurrency()).isEqualTo("USD");
        assertThat(saved.getType()).isEqualTo("Common Stock");
        // Die API liefert keine ISIN
        assertThat(saved.getIsin()).isNull();
    }

    @Test
    void search_existingAsset_isReusedAndNotSavedAgain() {
        // Arrange: Die Anlage gibt es schon in der Datenbank
        Asset existing = new Asset("AAPL", "US0378331005", "Apple Inc.", "NASDAQ", "USD", "Common Stock", "XNAS");
        when(marketDataProvider.search("apple")).thenReturn(List.of(hit("AAPL")));
        when(assetRepository.findBySymbolAndMicCode("AAPL", "XNAS")).thenReturn(Optional.of(existing));

        // Act + Assert: Es kommt genau das vorhandene Objekt zurück
        assertThat(service.search("apple", null)).containsExactly(existing);

        // Es wird nichts doppelt gespeichert
        verify(assetRepository, never()).save(any());
    }

    // ---------- limit ----------

    @Test
    void search_withoutLimit_returnsAtMost10() {
        // Arrange: API liefert 12 Treffer
        when(marketDataProvider.search("apple")).thenReturn(hits(12));

        // Act + Assert: Standardwert 10
        assertThat(service.search("apple", null)).hasSize(10);
    }

    @Test
    void search_withLimit_cutsResultsBeforeSaving() {
        // Arrange: API liefert 5 Treffer
        when(marketDataProvider.search("apple")).thenReturn(hits(5));

        // Act + Assert: nur 3 zurückgegeben
        assertThat(service.search("apple", 3)).hasSize(3);

        // Und auch nur 3 gespeichert: gekürzt wird VOR dem Speichern
        verify(assetRepository, times(3)).save(any());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 25})
    void search_limitAtBoundary_isAccepted(int limit) {
        assertThatCode(() -> service.search("apple", limit)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 26})
    void search_limitOutOfRange_isRejected(int limit) {
        assertRejected("apple", limit, "limit", Messages.LIMIT_RANGE);
    }

    // ---------- Suchbegriff ----------

    // Läuft mit null und mit jedem Wert aus @ValueSource
    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "a", " a "})
    void search_queryTooShort_isRejected(String query) {
        assertRejected(query, null, "q", Messages.QUERY_TOO_SHORT);
    }

    @Test
    void search_queryWithExactly2Characters_isAccepted() {
        assertThatCode(() -> service.search("ab", null)).doesNotThrowAnyException();
        verify(marketDataProvider).search("ab");
    }

    @Test
    void search_queryAndLimitInvalid_reportsBothFields() {
        assertThatThrownBy(() -> service.search("a", 0))
                .isInstanceOfSatisfying(ValidationException.class, e -> {
                    // Die Meldung ist die des ersten Fehlers
                    assertThat(e.getMessage()).isEqualTo(Messages.QUERY_TOO_SHORT);
                    // Beide Felder werden genannt, in dieser Reihenfolge
                    assertThat(e.getErrors())
                            .extracting(ValidationException.FieldError::field)
                            .containsExactly("q", "limit");
                });
        verify(marketDataProvider, never()).search(any());
    }

    // ---------- Verhalten der externen API ----------

    @Test
    void search_providerReturnsNothing_returnsEmptyList() {
        when(marketDataProvider.search("xyzxyz")).thenReturn(List.of());

        assertThat(service.search("xyzxyz", null)).isEmpty();
        verify(assetRepository, never()).save(any());
    }

    @Test
    void search_providerUnavailable_propagatesExceptionAndSavesNothing() {
        // Arrange: API ist nicht erreichbar
        UpstreamUnavailableException failure =
                new UpstreamUnavailableException("Kursdaten-Dienst nicht erreichbar.", null);
        when(marketDataProvider.search("apple")).thenThrow(failure);

        // Act + Assert: genau diese Exception wird weitergereicht
        assertThatThrownBy(() -> service.search("apple", null)).isSameAs(failure);
        verify(assetRepository, never()).save(any());
    }

    // ---------- Cache für Suchergebnisse (NF-02) ----------

    @Test
    void search_sameQueryTwice_callsProviderOnlyOnce() {
        when(marketDataProvider.search("apple")).thenReturn(List.of(hit("AAPL")));

        // Act: zweite Suche mit anderer Schreibweise und anderem Limit
        service.search("apple", null);
        List<Asset> second = service.search("Apple ", 5);

        // Assert: Die zweite Suche kommt aus dem Cache, liefert aber dieselben Anlagen
        verify(marketDataProvider, times(1)).search(any());
        assertThat(second).extracting(Asset::getSymbol).containsExactly("AAPL");
    }

    @Test
    void search_providerUnavailable_isNotCachedAndRetriedNextTime() {
        UpstreamUnavailableException failure =
                new UpstreamUnavailableException("Kursdaten-Dienst nicht erreichbar.", null);
        when(marketDataProvider.search("apple")).thenThrow(failure).thenReturn(List.of(hit("AAPL")));

        // Act: erste Suche scheitert, zweite fragt erneut bei der API nach
        assertThatThrownBy(() -> service.search("apple", null)).isSameAs(failure);
        List<Asset> second = service.search("apple", null);

        // Assert: Der Fehler wurde nicht zwischengespeichert
        verify(marketDataProvider, times(2)).search("apple");
        assertThat(second).extracting(Asset::getSymbol).containsExactly("AAPL");
    }

    // ---------- Hilfsmethoden ----------

    // Erzeugt einen Suchtreffer mit Standardwerten
    private static SearchHit hit(String symbol) {
        return new SearchHit(symbol, symbol + " Inc.", "NASDAQ", "XNAS", "USD", "Common Stock");
    }

    // Erzeugt beliebig viele verschiedene Suchtreffer (SYM1, SYM2, ...)
    private static List<SearchHit> hits(int count) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(i -> hit("SYM" + i))
                .toList();
    }

    // Prüft: Exception mit erwartetem Feldfehler, und die API wird nie aufgerufen
    private void assertRejected(String query, Integer limit, String expectedField, String expectedMessage) {
        assertThatThrownBy(() -> service.search(query, limit))
                .isInstanceOfSatisfying(ValidationException.class, e ->
                        assertThat(e.getErrors())
                                .contains(new ValidationException.FieldError(expectedField, expectedMessage)));
        verify(marketDataProvider, never()).search(any());
    }
}