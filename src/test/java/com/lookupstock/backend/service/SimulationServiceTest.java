package com.lookupstock.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

import com.lookupstock.backend.exception.InsufficientDataException;
import com.lookupstock.backend.exception.Messages;
import com.lookupstock.backend.exception.NotFoundException;
import com.lookupstock.backend.exception.UpstreamUnavailableException;
import com.lookupstock.backend.exception.ValidationException;
import com.lookupstock.backend.model.Asset;
import com.lookupstock.backend.model.Estimate;
import com.lookupstock.backend.model.EstimationResult;
import com.lookupstock.backend.model.Metrics;
import com.lookupstock.backend.model.Portfolio;
import com.lookupstock.backend.model.SimulationParameters;
import com.lookupstock.backend.model.SimulationResult;
import com.lookupstock.backend.model.SimulationRun;
import com.lookupstock.backend.model.TimelinePoint;
import com.lookupstock.backend.model.User;

/** BE #50: Ablauf laut Sequenzdiagramm US-04 und Fehlerfälle. */
class SimulationServiceTest {

    private static final User OWNER = new User("anna@example.com", "hash");

    private PortfolioService portfolioService;
    private ParameterEstimator estimator;
    private MonteCarloEngine engine;
    private MetricsCalculator metrics;
    private SimulationService service;

    private Asset asset;
    private Portfolio portfolio;
    private EstimationResult estimation;
    private final double[][] paths = new double[][] { { 1.0 } };
    private final Metrics sampleMetrics = new Metrics(58_000, 2, 1, 3, 0, 0.3, null);
    private final List<TimelinePoint> timeline = List.of(new TimelinePoint(0, 1, 2, 3));

    @BeforeEach
    void setUp() {
        portfolioService = mock(PortfolioService.class);
        estimator = mock(ParameterEstimator.class);
        engine = mock(MonteCarloEngine.class);
        metrics = mock(MetricsCalculator.class);
        service = new SimulationService(portfolioService, estimator, engine, metrics, () -> 731L);

        asset = new Asset("EUNL", null, "MSCI World", "XETRA", "EUR", "ETF", "XETR");
        ReflectionTestUtils.setField(asset, "id", 1L);
        portfolio = new Portfolio("Welt", OWNER);
        portfolio.addPosition(asset, new BigDecimal("100"));
        ReflectionTestUtils.setField(portfolio, "id", 5L);
        estimation = new EstimationResult(LocalDate.of(2016, 10, 1), LocalDate.of(2026, 9, 1),
                List.of(new Estimate(asset, 0.07, 0.15)));

        when(portfolioService.findById(5L, OWNER)).thenReturn(portfolio);
        when(estimator.estimate(anyList())).thenReturn(estimation);
        when(engine.simulate(anyList(), eq(estimation), any())).thenReturn(paths);
        when(metrics.percentiles(paths)).thenReturn(timeline);
        when(metrics.metrics(eq(paths), any())).thenReturn(sampleMetrics);
    }

    private static SimulationParameters params(Long seed) {
        return new SimulationParameters(10_000.0, 200.0, 20, 10_000, seed);
    }

    @Test
    void fuehrtSchritteInDerRichtigenReihenfolgeAus() {
        SimulationResult result = service.simulate(5L, params(42L), OWNER);

        InOrder order = inOrder(portfolioService, estimator, engine, metrics);
        order.verify(portfolioService).findById(5L, OWNER);
        order.verify(estimator).estimate(List.of(asset));
        order.verify(engine).simulate(portfolio.getWeights(), estimation, params(42L));
        order.verify(metrics).percentiles(paths);
        order.verify(metrics).metrics(paths, params(42L));

        assertThat(result.portfolioId()).isEqualTo(5L);
        assertThat(result.estimation()).isEqualTo(estimation);
        assertThat(result.timeline()).isEqualTo(timeline);
        assertThat(result.metrics()).isEqualTo(sampleMetrics);
        assertThat(result.runtimeMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void angegebenerSeedBleibtErhalten() {
        assertThat(service.simulate(5L, params(42L), OWNER).parameters().seed()).isEqualTo(42L);
    }

    @Test
    void ohneSeedWirdEinerErzeugtUndVerwendet() {
        SimulationResult result = service.simulate(5L, params(null), OWNER);

        assertThat(result.parameters().seed()).isEqualTo(731L);
        ArgumentCaptor<SimulationParameters> captor = ArgumentCaptor.forClass(SimulationParameters.class);
        verify(engine).simulate(anyList(), eq(estimation), captor.capture());
        assertThat(captor.getValue().seed()).isEqualTo(731L);
    }

    @Test
    void ungueltigeParameterWerdenVorDemLadenAbgelehnt() {
        SimulationParameters invalid = new SimulationParameters(10_000.0, 200.0, 41, 10_000, null);

        assertThatThrownBy(() -> service.simulate(5L, invalid, OWNER)).isInstanceOf(ValidationException.class);
        verifyNoInteractions(portfolioService, estimator, engine, metrics);
    }

    @Test
    void ohnePortfolioIdLiefert400() {
        assertThatThrownBy(() -> service.simulate(null, params(42L), OWNER))
                .isInstanceOf(ValidationException.class)
                .hasMessage(Messages.PORTFOLIO_ID_REQUIRED);
        verifyNoInteractions(portfolioService);
    }

    @Test
    void ohneParameterLiefert400() {
        assertThatThrownBy(() -> service.simulate(5L, null, OWNER))
                .isInstanceOf(ValidationException.class)
                .hasMessage(Messages.PARAMETERS_REQUIRED);
    }

    @Test
    void fremdesPortfolioLiefert404() {
        User other = new User("ben@example.com", "hash");
        when(portfolioService.findById(5L, other)).thenThrow(new NotFoundException(Messages.PORTFOLIO_NOT_FOUND));

        assertThatThrownBy(() -> service.simulate(5L, params(42L), other)).isInstanceOf(NotFoundException.class);
        verifyNoInteractions(estimator, engine);
    }

    @Test
    void anbieterAusfallWirdAls503Weitergegeben() {
        when(estimator.estimate(anyList()))
                .thenThrow(new UpstreamUnavailableException(Messages.UPSTREAM_UNAVAILABLE, null));

        assertThatThrownBy(() -> service.simulate(5L, params(42L), OWNER))
                .isInstanceOf(UpstreamUnavailableException.class);
        verifyNoInteractions(engine);
    }

    @Test
    void zuWenigKursdatenWirdAls422Weitergegeben() {
        when(estimator.estimate(anyList())).thenThrow(new InsufficientDataException(List.of(asset)));

        assertThatThrownBy(() -> service.simulate(5L, params(42L), OWNER))
                .isInstanceOf(InsufficientDataException.class);
        verifyNoInteractions(engine);
    }

    @Test
    void runLiefertVerlaufKennzahlenUndEndwerte() {
        double[][] twoPaths = new double[][] { { 10_000, 10_500, 11_000 }, { 10_000, 9_500, 9_000 } };
        SimulationParameters effective = params(42L);
        when(engine.simulate(anyList(), eq(estimation), eq(effective))).thenReturn(twoPaths);
        when(metrics.percentiles(twoPaths)).thenReturn(timeline);
        when(metrics.metrics(twoPaths, effective)).thenReturn(sampleMetrics);

        SimulationRun run = service.run(portfolio.getWeights(), estimation, effective);

        assertThat(run.timeline()).isEqualTo(timeline);
        assertThat(run.metrics()).isEqualTo(sampleMetrics);
        assertThat(run.endValues()).containsExactly(11_000, 9_000);
    }

    @Test
    void ensureSeedBehaeltVorhandenenSeedUndErgaenztFehlenden() {
        assertThat(service.ensureSeed(params(42L)).seed()).isEqualTo(42L);
        assertThat(service.ensureSeed(params(null)).seed()).isEqualTo(731L);
    }
}
