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
import com.lookupstock.backend.exception.Messages;
import com.lookupstock.backend.exception.NotFoundException;
import com.lookupstock.backend.model.Asset;
import com.lookupstock.backend.model.Portfolio;
import com.lookupstock.backend.model.Position;
import com.lookupstock.backend.model.User;
import com.lookupstock.backend.repository.AssetRepository;
import com.lookupstock.backend.repository.PortfolioRepository;

class PortfolioServiceTest {

    // Angemeldeter Nutzer, dem die angelegten Portfolios gehören
    private static final User OWNER = new User("anna@example.com", "hash");

    // Ein zweiter Nutzer, dem die Portfolios von OWNER nicht gehören
    private static final User OTHER = new User("ben@example.com", "hash");

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
    void findAll_returnsPortfoliosOfOwnerFromRepository() {
        // Arrange: Repository liefert zwei Portfolios des Nutzers
        Portfolio first = new Portfolio("Depot A", OWNER);
        Portfolio second = new Portfolio("Depot B", OWNER);
        when(portfolioRepository.findAllByOwnerOrderByCreatedAtDesc(OWNER)).thenReturn(List.of(first, second));

        // Act
        List<Portfolio> result = service.findAll(OWNER);

        // Assert: genau diese zwei, in dieser Reihenfolge
        assertThat(result).containsExactly(first, second);
    }

    // ---------- findById ----------

    @Test
    void findById_existingId_returnsPortfolio() {
        // Arrange: Repository findet das Portfolio
        Portfolio portfolio = new Portfolio("Depot A", OWNER);
        when(portfolioRepository.findByIdAndOwner(1L, OWNER)).thenReturn(Optional.of(portfolio));

        // Act + Assert: es kommt genau dasselbe Objekt zurück
        assertThat(service.findById(1L, OWNER)).isSameAs(portfolio);
    }

    @Test
    void findById_unknownId_throwsNotFound() {
        // Arrange: Repository findet nichts, die ID gibt es nicht
        when(portfolioRepository.findByIdAndOwner(99L, OWNER)).thenReturn(Optional.empty());

        // Act + Assert: Exception mit der erwarteten Meldung
        assertThatThrownBy(() -> service.findById(99L, OWNER))
                .isInstanceOf(NotFoundException.class)
                .hasMessage(Messages.PORTFOLIO_NOT_FOUND);
    }

    @Test
    void findById_portfolioEinesAnderenNutzers_liefert404_NF04() {
        // Arrange: Portfolio 1 gehört OWNER, für OTHER findet das Repository nichts
        when(portfolioRepository.findByIdAndOwner(1L, OTHER)).thenReturn(Optional.empty());

        // Act + Assert: dieselbe Meldung wie bei einer unbekannten ID
        assertThatThrownBy(() -> service.findById(1L, OTHER))
                .isInstanceOf(NotFoundException.class)
                .hasMessage(Messages.PORTFOLIO_NOT_FOUND);

        // Das ungefilterte findById darf nie benutzt werden
        verify(portfolioRepository, never()).findById(any());
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
                position(2L, "40")), OWNER);

        // Assert: Das Portfolio, das an save() ging, abfangen und prüfen
        ArgumentCaptor<Portfolio> captor = ArgumentCaptor.forClass(Portfolio.class);
        verify(portfolioRepository).save(captor.capture());
        Portfolio saved = captor.getValue();

        assertThat(saved.getName()).isEqualTo("Mein Depot");
        assertThat(saved.getOwner()).isSameAs(OWNER);
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
        assertThatThrownBy(() -> service.create(request("Mein Depot", position(99L, "100")), OWNER))
                .isInstanceOfSatisfying(ValidationException.class, e -> {
                    assertThat(e.getMessage()).isEqualTo(Messages.ASSET_NOT_FOUND);
                    assertThat(e.getErrors()).containsExactly(
                            new ValidationException.FieldError("positions", Messages.unknownAssetDetail(99L)));
                });

        // Ungültige Daten dürfen nie gespeichert werden
        verify(portfolioRepository, never()).save(any());
    }

    @Test
    void create_missingAssetId_isRejected() {
        givenAssets();
        assertRejected(request("Mein Depot", position(null, "100")),
                "positions", Messages.unknownAssetDetail(null));
    }

    // ---------- create: fachliche Regeln (Portfolio.validate) ----------

    @Test
    void create_blankName_isRejected() {
        givenAssets(asset(1L, "AAPL"));
        assertRejected(request("   ", position(1L, "100")),
                "name", Messages.NAME_INVALID);
    }

    @Test
    void create_nameWith101Characters_isRejected() {
        givenAssets(asset(1L, "AAPL"));
        assertRejected(request("a".repeat(101), position(1L, "100")),
                "name", Messages.NAME_INVALID);
    }

    @Test
    void create_withoutPositions_isRejected() {
        // positions ist null: Der Service macht daraus eine leere Liste
        assertRejected(new PortfolioRequest(null, "Mein Depot", null),
                "positions", Messages.POSITIONS_EMPTY);
    }

    @Test
    void create_weightsNotSummingTo100_isRejected() {
        givenAssets(asset(1L, "AAPL"), asset(2L, "MSFT"));
        assertRejected(request("Mein Depot", position(1L, "60"), position(2L, "30")),
                "positions", Messages.weightSumDetail(new BigDecimal("90")));
    }

    // Derselbe Test läuft einmal mit "0" und einmal mit "-10"
    @ParameterizedTest
    @ValueSource(strings = {"0", "-10"})
    void create_weightZeroOrNegative_isRejected(String weight) {
        givenAssets(asset(1L, "AAPL"), asset(2L, "MSFT"));
        assertRejected(request("Mein Depot", position(1L, weight), position(2L, "100")),
                "positions", Messages.WEIGHT_NOT_POSITIVE);
    }

    @Test
    void create_weightWithThreeDecimals_isRejected() {
        givenAssets(asset(1L, "AAPL"), asset(2L, "MSFT"));
        // Summe ist genau 100, nur die Nachkommastellen sind falsch
        assertRejected(request("Mein Depot", position(1L, "33.333"), position(2L, "66.667")),
                "positions", Messages.WEIGHT_TOO_MANY_DECIMALS);
    }

    @Test
    void create_sameAssetTwice_isRejected() {
        givenAssets(asset(1L, "AAPL"));
        assertRejected(request("Mein Depot", position(1L, "50"), position(1L, "50")),
                "positions", Messages.DUPLICATE_ASSET);
    }

    // ---------- create: Grenzwerte ----------

    @Test
    void create_nameWithExactly100Characters_isAccepted() {
        givenAssets(asset(1L, "AAPL"));

        // 100 Zeichen sind gerade noch erlaubt
        assertThatCode(() -> service.create(request("a".repeat(100), position(1L, "100")), OWNER))
                .doesNotThrowAnyException();
        verify(portfolioRepository).save(any());
    }

    @Test
    void create_sumOf99_99_isAcceptedWithinTolerance() {
        givenAssets(asset(1L, "AAPL"), asset(2L, "MSFT"));

        // Abweichung 0,01 liegt noch innerhalb der Toleranz
        assertThatCode(() -> service.create(request("Mein Depot",
                position(1L, "49.99"), position(2L, "50")), OWNER))
                .doesNotThrowAnyException();
        verify(portfolioRepository).save(any());
    }

    @Test
    void create_sumOf99_98_isRejected() {
        givenAssets(asset(1L, "AAPL"), asset(2L, "MSFT"));

        // Abweichung 0,02 liegt außerhalb der Toleranz
        assertRejected(request("Mein Depot", position(1L, "49.98"), position(2L, "50")),
                "positions", Messages.weightSumDetail(new BigDecimal("99.98")));
    }

    // ---------- Hilfsmethoden ----------

    // Erzeugt eine Test-Anlage mit gesetzter ID
    // ---------- update (US-06) ----------

    @Test
    void update_aendertNameGewichtungenUndAnlagen() {
        Asset apple = asset(1L, "AAPL");
        Asset microsoft = asset(2L, "MSFT");
        Asset nvidia = asset(3L, "NVDA");
        Portfolio existing = new Portfolio("Alt", OWNER);
        existing.addPosition(apple, new BigDecimal("50"));
        existing.addPosition(microsoft, new BigDecimal("50"));
        Position applePosition = existing.getPositions().get(0);
        when(portfolioRepository.findByIdAndOwner(1L, OWNER)).thenReturn(Optional.of(existing));
        givenAssets(apple, nvidia);

        // AAPL bleibt mit neuer Gewichtung, MSFT fällt weg, NVDA kommt dazu
        Portfolio result = service.update(1L, request("Neu", position(1L, "70"), position(3L, "30")), OWNER);

        assertThat(result).isSameAs(existing);
        assertThat(result.getName()).isEqualTo("Neu");
        assertThat(result.getPositions()).extracting(p -> p.getAsset().getSymbol()).containsExactly("AAPL", "NVDA");
        assertThat(result.getPositions()).extracting(Position::getWeight)
                .containsExactly(new BigDecimal("70"), new BigDecimal("30"));
        // bestehende Position wird geändert, nicht neu angelegt (UNIQUE portfolio_id, asset_id)
        assertThat(result.getPositions().get(0)).isSameAs(applePosition);
        assertThat(result.getUpdatedAt()).isNotNull();
        verify(portfolioRepository).save(existing);
    }

    @Test
    void update_ungueltigeGewichtung_laesstPortfolioUnveraendert() {
        Asset apple = asset(1L, "AAPL");
        Portfolio existing = new Portfolio("Alt", OWNER);
        existing.addPosition(apple, new BigDecimal("100"));
        when(portfolioRepository.findByIdAndOwner(1L, OWNER)).thenReturn(Optional.of(existing));
        givenAssets(apple);

        assertThatThrownBy(() -> service.update(1L, request("Neu", position(1L, "90")), OWNER))
                .isInstanceOf(ValidationException.class)
                .hasMessage(Messages.WEIGHT_SUM);

        assertThat(existing.getName()).isEqualTo("Alt");
        assertThat(existing.getPositions()).extracting(Position::getWeight).containsExactly(new BigDecimal("100"));
        verify(portfolioRepository, never()).save(any());
    }

    @Test
    void update_fremdesPortfolio_liefert404_NF04() {
        when(portfolioRepository.findByIdAndOwner(1L, OTHER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(1L, request("X", position(1L, "100")), OTHER))
                .isInstanceOf(NotFoundException.class)
                .hasMessage(Messages.PORTFOLIO_NOT_FOUND);
        verify(portfolioRepository, never()).save(any());
    }

    // ---------- delete (US-06) ----------

    @Test
    void delete_eigenesPortfolio_wirdGeloescht() {
        Portfolio existing = new Portfolio("Alt", OWNER);
        when(portfolioRepository.findByIdAndOwner(1L, OWNER)).thenReturn(Optional.of(existing));

        service.delete(1L, OWNER);

        verify(portfolioRepository).delete(existing);
    }

    @Test
    void delete_fremdesPortfolio_liefert404_NF04() {
        when(portfolioRepository.findByIdAndOwner(1L, OTHER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(1L, OTHER))
                .isInstanceOf(NotFoundException.class);
        verify(portfolioRepository, never()).delete(any());
    }

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
        assertThatThrownBy(() -> service.create(request, OWNER))
                .isInstanceOfSatisfying(ValidationException.class, e ->
                        assertThat(e.getErrors())
                                .contains(new ValidationException.FieldError(expectedField, expectedMessage)));
        verify(portfolioRepository, never()).save(any());
    }
}