package com.lookupstock.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.lookupstock.backend.exception.InsufficientDataException;
import com.lookupstock.backend.exception.Messages;
import com.lookupstock.backend.exception.NotFoundException;
import com.lookupstock.backend.exception.ValidationException;
import com.lookupstock.backend.model.Asset;
import com.lookupstock.backend.model.ComparisonEntry;
import com.lookupstock.backend.model.ComparisonResult;
import com.lookupstock.backend.model.Estimate;
import com.lookupstock.backend.model.EstimationResult;
import com.lookupstock.backend.model.Portfolio;
import com.lookupstock.backend.model.SimulationParameters;
import com.lookupstock.backend.model.User;
import com.lookupstock.backend.repository.AssetRepository;

/**
 * BE #69: Ablauf des Vergleichs laut ADR-05. Engine und Kennzahlen laufen echt,
 * damit gleicher Seed und gleiche Zufallszahlen tatsächlich geprüft werden (NF-08).
 */
class ComparisonServiceTest {

    private static final User OWNER = new User("anna@example.com", "hash");
    private static final Instant NOW = Instant.parse("2026-10-20T09:00:00Z");

    private PortfolioService portfolioService;
    private AssetRepository assetRepository;
    private ParameterEstimator estimator;
    private ComparisonService service;

    private Asset world;
    private Asset bonds;
    private Portfolio portfolio;

    @BeforeEach
    void setUp() {
        portfolioService = mock(PortfolioService.class);
        assetRepository = mock(AssetRepository.class);
        estimator = mock(ParameterEstimator.class);
        SimulationService simulationService = new SimulationService(portfolioService, estimator,
                new MonteCarloEngine(), new MetricsCalculator(), () -> 731L);
        service = new ComparisonService(portfolioService, assetRepository, estimator, simulationService,
                Clock.fixed(NOW, ZoneOffset.UTC));

        world = asset(1L, "EUNL", "MSCI World");
        bonds = asset(2L, "IS3N", "Staatsanleihen");

        portfolio = new Portfolio("Mein Weltportfolio", OWNER);
        portfolio.addPosition(world, new BigDecimal("70"));
        portfolio.addPosition(bonds, new BigDecimal("30"));
        ReflectionTestUtils.setField(portfolio, "id", 5L);

        when(portfolioService.findById(5L, OWNER)).thenReturn(portfolio);
        when(assetRepository.findById(1L)).thenReturn(Optional.of(world));
        when(assetRepository.findById(2L)).thenReturn(Optional.of(bonds));
        // world deutlich besser als bonds, beide mit kleiner Schwankung: Ergebnis des Paarvergleichs ist eindeutig
        when(estimator.estimate(anyList())).thenReturn(new EstimationResult(
                LocalDate.of(2016, 10, 1), LocalDate.of(2026, 9, 1),
                List.of(new Estimate(world, 0.50, 0.01), new Estimate(bonds, 0.0, 0.01))));
    }

    private static Asset asset(Long id, String symbol, String name) {
        Asset asset = new Asset(symbol, null, name, "XETRA", "EUR", "ETF", "XETR");
        ReflectionTestUtils.setField(asset, "id", id);
        return asset;
    }

    private static SimulationParameters params(Long seed) {
        return new SimulationParameters(10_000.0, 100.0, 2, 1_000, seed);
    }

    private static ComparisonEntry ofPortfolio(String label, Long id) {
        return new ComparisonEntry(label, id, null);
    }

    private static ComparisonEntry ofAsset(String label, Long id) {
        return new ComparisonEntry(label, null, id);
    }

    @Test
    void vergleichtPortfolioUndAnlageMitEinerGemeinsamenSchaetzung() {
        ComparisonResult result = service.compare(
                List.of(ofPortfolio(null, 5L), ofAsset(null, 2L)), params(42L), OWNER);

        // jede Anlage nur einmal, ein Aufruf für beide Einträge (E17)
        verify(estimator, times(1)).estimate(List.of(world, bonds));
        assertThat(result.items()).hasSize(2);
        assertThat(result.items().get(0).portfolioId()).isEqualTo(5L);
        assertThat(result.items().get(0).assetId()).isNull();
        assertThat(result.items().get(1).assetId()).isEqualTo(2L);
        assertThat(result.items().get(1).portfolioId()).isNull();
        assertThat(result.estimation().historyFrom()).isEqualTo(LocalDate.of(2016, 10, 1));
        assertThat(result.createdAt()).isEqualTo(NOW);
        // Verlauf von Monat 0 bis 24
        assertThat(result.items().get(0).timeline()).hasSize(25);
        assertThat(result.items().get(0).metrics().totalContributions()).isEqualTo(12_400.0);
    }

    @Test
    void ohneBezeichnungWerdenDieNamenVerwendet() {
        ComparisonResult result = service.compare(
                List.of(ofPortfolio("  ", 5L), ofAsset(null, 2L)), params(42L), OWNER);

        assertThat(result.items().get(0).label()).isEqualTo("Mein Weltportfolio");
        assertThat(result.items().get(1).label()).isEqualTo("Staatsanleihen");
    }

    @Test
    void eigeneBezeichnungWirdGetrimmtUebernommen() {
        ComparisonResult result = service.compare(
                List.of(ofPortfolio(" Welt 70/30 ", 5L), ofAsset("Nur Anleihen", 2L)), params(42L), OWNER);

        assertThat(result.items().get(0).label()).isEqualTo("Welt 70/30");
        assertThat(result.items().get(1).label()).isEqualTo("Nur Anleihen");
    }

    @Test
    void ohneSeedWirdEinSeedFuerBeideEintraegeErzeugt() {
        ComparisonResult result = service.compare(
                List.of(ofAsset(null, 1L), ofAsset(null, 1L)), params(null), OWNER);

        assertThat(result.parameters().seed()).isEqualTo(731L);
        // gleiche Anlage, gleiche Zufallszahlen: identische Ergebnisse
        assertThat(result.items().get(0).timeline()).isEqualTo(result.items().get(1).timeline());
        assertThat(result.items().get(0).metrics()).isEqualTo(result.items().get(1).metrics());
        verify(estimator).estimate(List.of(world));
    }

    @Test
    void gleicherSeedLiefertGleichesErgebnis() {
        List<ComparisonEntry> entries = List.of(ofPortfolio(null, 5L), ofAsset(null, 2L));

        ComparisonResult first = service.compare(entries, params(42L), OWNER);
        ComparisonResult second = service.compare(entries, params(42L), OWNER);

        assertThat(second.items()).isEqualTo(first.items());
        assertThat(second.pairwise()).isEqualTo(first.pairwise());
    }

    @Test
    void paarvergleichZaehltPfadeMitHoeheremEndwert() {
        ComparisonResult result = service.compare(
                List.of(ofAsset("Welt", 1L), ofAsset("Anleihen", 2L)), params(42L), OWNER);

        assertThat(result.pairwise()).hasSize(1);
        ComparisonResult.Pairwise pair = result.pairwise().getFirst();
        assertThat(pair.a()).isZero();
        assertThat(pair.b()).isEqualTo(1);
        assertThat(pair.probabilityAOutperformsB()).isEqualTo(1.0);
    }

    @Test
    void gleicheEintraegeSindNieBesser() {
        ComparisonResult result = service.compare(
                List.of(ofAsset(null, 1L), ofAsset(null, 1L)), params(42L), OWNER);

        assertThat(result.pairwise().getFirst().probabilityAOutperformsB()).isZero();
    }

    @Test
    void falscheAnzahlAnEintraegenLiefert400() {
        for (List<ComparisonEntry> entries : Arrays.asList(
                null,
                List.<ComparisonEntry>of(),
                List.of(ofAsset(null, 1L)),
                List.of(ofAsset(null, 1L), ofAsset(null, 2L), ofPortfolio(null, 5L)))) {
            assertThatThrownBy(() -> service.compare(entries, params(42L), OWNER))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage(Messages.COMPARISON_ITEM_COUNT);
        }
        verifyNoInteractions(portfolioService, estimator);
    }

    @Test
    void eintragOhneOderMitBeidenIdsLiefert400() {
        List<ComparisonEntry> entries = List.of(new ComparisonEntry(null, null, null),
                new ComparisonEntry(null, 5L, 2L));

        Throwable thrown = catchThrowable(() -> service.compare(entries, params(42L), OWNER));

        assertThat(thrown).isInstanceOf(ValidationException.class).hasMessage(Messages.COMPARISON_ITEM_INVALID);
        assertThat(((ValidationException) thrown).getErrors())
                .extracting(ValidationException.FieldError::field)
                .containsExactly("items[0]", "items[1]");
        verifyNoInteractions(portfolioService, estimator);
    }

    @Test
    void ungueltigeParameterLiefern400WieBeiDerSimulation() {
        SimulationParameters invalid = new SimulationParameters(10_000.0, 100.0, 41, 1_000, 42L);

        assertThatThrownBy(() -> service.compare(List.of(ofAsset(null, 1L), ofAsset(null, 2L)), invalid, OWNER))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.compare(List.of(ofAsset(null, 1L), ofAsset(null, 2L)), null, OWNER))
                .isInstanceOf(ValidationException.class)
                .hasMessage(Messages.PARAMETERS_REQUIRED);
        verifyNoInteractions(estimator);
    }

    @Test
    void fremdesPortfolioLiefert404() {
        User other = new User("ben@example.com", "hash");
        when(portfolioService.findById(5L, other)).thenThrow(new NotFoundException(Messages.PORTFOLIO_NOT_FOUND));

        assertThatThrownBy(() -> service.compare(List.of(ofPortfolio(null, 5L), ofAsset(null, 2L)), params(42L), other))
                .isInstanceOf(NotFoundException.class);
        verifyNoInteractions(estimator);
    }

    @Test
    void unbekannteAnlageLiefert404() {
        when(assetRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.compare(List.of(ofAsset(null, 1L), ofAsset(null, 99L)), params(42L), OWNER))
                .isInstanceOf(NotFoundException.class)
                .hasMessage(Messages.COMPARISON_ASSET_NOT_FOUND);
        verifyNoInteractions(estimator);
    }

    @Test
    void zuWenigKursdatenWirdAls422Weitergegeben() {
        when(estimator.estimate(anyList())).thenThrow(new InsufficientDataException(List.of(bonds)));

        assertThatThrownBy(() -> service.compare(List.of(ofPortfolio(null, 5L), ofAsset(null, 1L)), params(42L), OWNER))
                .isInstanceOf(InsufficientDataException.class);
    }
}
