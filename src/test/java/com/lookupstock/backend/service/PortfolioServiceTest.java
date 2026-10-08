package com.lookupstock.backend.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.test.util.ReflectionTestUtils;
import static org.mockito.Mockito.never;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.lookupstock.backend.exception.ValidationException;
import static org.assertj.core.api.Assertions.assertThatCode;
import com.lookupstock.backend.dto.PortfolioRequest;
import com.lookupstock.backend.dto.PositionRequest;
import com.lookupstock.backend.exception.NotFoundException;
import com.lookupstock.backend.model.Asset;
import com.lookupstock.backend.model.Portfolio;
import com.lookupstock.backend.model.Position;
import com.lookupstock.backend.repository.AssetRepository;
import com.lookupstock.backend.repository.PortfolioRepository;

class PortfolioServiceTest {

    // Gemockte Repositories statt echter Datenbank
    private PortfolioRepository portfolioRepository;
    private AssetRepository assetRepository;

    // Der echte Service, der getestet wird
    private PortfolioService service;

    // Läuft vor JEDEM Test: frische Mocks, damit sich Tests nicht gegenseitig beeinflussen
    @BeforeEach
    void setUp() {
        portfolioRepository = mock(PortfolioRepository.class);
        assetRepository = mock(AssetRepository.class);
        service = new PortfolioService(portfolioRepository, assetRepository);

        // ★ save() gibt das übergebene Portfolio unverändert zurück, wie die echte Datenbank
        when(portfolioRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    // ---------- findAll ----------

    @Test
    void findAll_returnsAllPortfoliosFromRepository() {
        // Arrange: Repository liefert zwei Portfolios
        Portfolio first = new Portfolio("Depot A");
        Portfolio second = new Portfolio("Depot B");
        when(portfolioRepository.findAll()).thenReturn(List.of(first, second));

        // Act
        List<Portfolio> result = service.findAll();

        // Assert: genau diese zwei, in dieser Reihenfolge
        assertThat(result).containsExactly(first, second);
    }

    // ---------- findById ----------

    @Test
    void findById_existingId_returnsPortfolio() {
        // Arrange: Repository findet das Portfolio
        Portfolio portfolio = new Portfolio("Depot A");
        when(portfolioRepository.findById(1L)).thenReturn(Optional.of(portfolio));

        // Act + Assert: es kommt genau dasselbe Objekt zurück
        assertThat(service.findById(1L)).isSameAs(portfolio);
    }

    @Test
    void findById_unknownId_throwsNotFound() {
        // Arrange: Repository findet nichts
        when(portfolioRepository.findById(99L)).thenReturn(Optional.empty());

        // Act + Assert: Exception mit der erwarteten Meldung
        assertThatThrownBy(() -> service.findById(99L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Portfolio nicht gefunden.");
    }

    // ---------- create: Erfolgsfall ----------

    @Test
    void create_validPortfolio_savesAndReturnsIt() {
        // Arrange: zwei bekannte Anlagen in der "Datenbank"
        Asset apple = asset(1L, "AAPL");
        Asset microsoft = asset(2L, "MSFT");
        when(assetRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(apple, microsoft));

        // Act: Portfolio mit 60 % + 40 % anlegen
        Portfolio result = service.create(request("Mein Depot",
                position(1L, "60"),
                position(2L, "40")));

        // Assert: Das Portfolio, das an save() ging, abfangen und prüfen
        ArgumentCaptor<Portfolio> captor = ArgumentCaptor.forClass(Portfolio.class);
        verify(portfolioRepository).save(captor.capture());
        Portfolio saved = captor.getValue();

        assertThat(saved.getName()).isEqualTo("Mein Depot");
        assertThat(saved.getPositions())
                .extracting(p -> p.getAsset().getSymbol())
                .containsExactly("AAPL", "MSFT");
        assertThat(saved.getPositions())
                .extracting(Position::getWeight)
                .containsExactly(new BigDecimal("60"), new BigDecimal("40"));

        // Zurückgegeben wird genau das gespeicherte Portfolio
        assertThat(result).isSameAs(saved);
    }

    // ---------- create: unbekannte Anlagen (Prüfung im Service) ----------

    @Test
    void create_unknownAssetId_throwsValidationAndDoesNotSave() {
        // Arrange: Die Datenbank kennt keine Anlage mit ID 99
        givenAssets();

        // Act + Assert: Exception mit Gesamtmeldung und Feldfehler
        assertThatThrownBy(() -> service.create(request("Mein Depot", position(99L, "100"))))
                .isInstanceOfSatisfying(ValidationException.class, e -> {
                    assertThat(e.getMessage()).isEqualTo("Das Portfolio enthält unbekannte Anlagen.");
                    assertThat(e.getErrors()).containsExactly(
                            new ValidationException.FieldError("positions", "Unbekannte Anlage: 99"));
                });

        // Ungültige Daten dürfen nie gespeichert werden
        verify(portfolioRepository, never()).save(any());
    }

    @Test
    void create_missingAssetId_isRejected() {
        givenAssets();
        assertRejected(request("Mein Depot", position(null, "100")),
                "positions", "Unbekannte Anlage: null");
    }

    // ---------- create: fachliche Regeln (Portfolio.validate) ----------

    @Test
    void create_blankName_isRejected() {
        givenAssets(asset(1L, "AAPL"));
        assertRejected(request("   ", position(1L, "100")),
                "name", "Bitte einen Namen eingeben.");
    }

    @Test
    void create_nameWith101Characters_isRejected() {
        givenAssets(asset(1L, "AAPL"));
        assertRejected(request("a".repeat(101), position(1L, "100")),
                "name", "Der Name darf höchstens 100 Zeichen haben.");
    }

    @Test
    void create_withoutPositions_isRejected() {
        // positions ist null: Der Service macht daraus eine leere Liste
        assertRejected(new PortfolioRequest(null, "Mein Depot", null),
                "positions", "Bitte mindestens eine Anlage hinzufügen.");
    }

    @Test
    void create_weightsNotSummingTo100_isRejected() {
        givenAssets(asset(1L, "AAPL"), asset(2L, "MSFT"));
        assertRejected(request("Mein Depot", position(1L, "60"), position(2L, "30")),
                "positions", "Summe der Gewichtungen ist 90 %.");
    }

    // Derselbe Test läuft einmal mit "0" und einmal mit "-10"
    @ParameterizedTest
    @ValueSource(strings = {"0", "-10"})
    void create_weightZeroOrNegative_isRejected(String weight) {
        givenAssets(asset(1L, "AAPL"), asset(2L, "MSFT"));
        assertRejected(request("Mein Depot", position(1L, weight), position(2L, "100")),
                "positions", "Jede Gewichtung muss größer als 0 % sein.");
    }

    @Test
    void create_weightWithThreeDecimals_isRejected() {
        givenAssets(asset(1L, "AAPL"), asset(2L, "MSFT"));
        // Summe ist genau 100, nur die Nachkommastellen sind falsch
        assertRejected(request("Mein Depot", position(1L, "33.333"), position(2L, "66.667")),
                "positions", "Gewichtungen dürfen höchstens 2 Nachkommastellen haben.");
    }

    @Test
    void create_sameAssetTwice_isRejected() {
        givenAssets(asset(1L, "AAPL"));
        assertRejected(request("Mein Depot", position(1L, "50"), position(1L, "50")),
                "positions", "Die Anlage AAPL ist doppelt enthalten.");
    }

    // ---------- create: Grenzwerte ----------

    @Test
    void create_nameWithExactly100Characters_isAccepted() {
        givenAssets(asset(1L, "AAPL"));

        // 100 Zeichen sind gerade noch erlaubt
        assertThatCode(() -> service.create(request("a".repeat(100), position(1L, "100"))))
                .doesNotThrowAnyException();
        verify(portfolioRepository).save(any());
    }

    @Test
    void create_sumOf99_99_isAcceptedWithinTolerance() {
        givenAssets(asset(1L, "AAPL"), asset(2L, "MSFT"));

        // Abweichung 0,01 liegt noch innerhalb der Toleranz
        assertThatCode(() -> service.create(request("Mein Depot",
                position(1L, "49.99"), position(2L, "50"))))
                .doesNotThrowAnyException();
        verify(portfolioRepository).save(any());
    }

    @Test
    void create_sumOf99_98_isRejected() {
        givenAssets(asset(1L, "AAPL"), asset(2L, "MSFT"));

        // Abweichung 0,02 liegt außerhalb der Toleranz
        assertRejected(request("Mein Depot", position(1L, "49.98"), position(2L, "50")),
                "positions", "Summe der Gewichtungen ist 99.98 %.");
    }

    // ---------- Hilfsmethoden ----------

    // Erzeugt eine Test-Anlage mit gesetzter ID
    private static Asset asset(long id, String symbol) {
        Asset asset = new Asset(symbol, null, symbol + " Inc.", "NASDAQ", "USD", "Common Stock", "XNAS");
        // Die ID vergibt sonst die Datenbank. Im Test setzen wir das private Feld direkt
        ReflectionTestUtils.setField(asset, "id", id);
        return asset;
    }

    // Erzeugt eine Position für den Request, Gewicht als Text für exakte Dezimalzahlen
    private static PositionRequest position(Long assetId, String weight) {
        return new PositionRequest(assetId, new BigDecimal(weight));
    }

    // Erzeugt einen Portfolio-Request, das Token wird im Service nicht verwendet
    private static PortfolioRequest request(String name, PositionRequest... positions) {
        return new PortfolioRequest(null, name, List.of(positions));
    }

        // Legt fest, welche Anlagen das gemockte Repository "kennt"
    private void givenAssets(Asset... assets) {
        when(assetRepository.findAllById(any())).thenReturn(List.of(assets));
    }

    // Prüft: Exception fliegt, enthält den erwarteten Feldfehler, und nichts wird gespeichert
    private void assertRejected(PortfolioRequest request, String expectedField, String expectedMessage) {
        assertThatThrownBy(() -> service.create(request))
                .isInstanceOfSatisfying(ValidationException.class, e ->
                        assertThat(e.getErrors())
                                .contains(new ValidationException.FieldError(expectedField, expectedMessage)));
        verify(portfolioRepository, never()).save(any());
    }
}