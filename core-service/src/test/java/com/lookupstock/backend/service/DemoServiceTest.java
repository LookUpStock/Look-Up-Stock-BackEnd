package com.lookupstock.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.lookupstock.backend.exception.Messages;
import com.lookupstock.backend.exception.NotFoundException;
import com.lookupstock.backend.exception.ValidationException;
import com.lookupstock.backend.model.Asset;
import com.lookupstock.backend.model.Estimate;
import com.lookupstock.backend.model.EstimationResult;
import com.lookupstock.backend.model.Metrics;
import com.lookupstock.backend.model.SimulationParameters;
import com.lookupstock.backend.model.SimulationResult;
import com.lookupstock.backend.model.SimulationRun;
import com.lookupstock.backend.model.TimelinePoint;
import com.lookupstock.backend.model.UsageEventType;
import com.lookupstock.backend.model.WeightedAsset;
import com.lookupstock.backend.queue.UsageEventRecorder;
import com.lookupstock.backend.repository.AssetRepository;

/** US-09: Beispielsimulation im Gastmodus, ohne Speichern. */
class DemoServiceTest {

    private AssetRepository assetRepository;
    private ParameterEstimator estimator;
    private SimulationService simulationService;
    private UsageEventRecorder usageEventRecorder;
    private DemoService service;

    private Asset world;
    private Asset emergingMarkets;
    private EstimationResult estimation;
    private final SimulationRun run = new SimulationRun(
            List.of(new TimelinePoint(0, 1, 2, 3)), new Metrics(58_000, 2, 1, 3, 0, 0.3, null), new double[] { 1.0 });

    @BeforeEach
    void setUp() {
        assetRepository = mock(AssetRepository.class);
        estimator = mock(ParameterEstimator.class);
        simulationService = mock(SimulationService.class);
        usageEventRecorder = mock(UsageEventRecorder.class);
        service = new DemoService(assetRepository, estimator, simulationService, usageEventRecorder);

        world = new Asset("URTH", null, "iShares MSCI World ETF", "NYSE", "USD", "ETF", "ARCX");
        ReflectionTestUtils.setField(world, "id", 1L);
        emergingMarkets = new Asset("IEMG", null, "iShares Core MSCI Emerging Markets ETF", "NYSE", "USD", "ETF", "ARCX");
        ReflectionTestUtils.setField(emergingMarkets, "id", 2L);
        estimation = new EstimationResult(LocalDate.of(2016, 10, 1), LocalDate.of(2026, 9, 1),
                List.of(new Estimate(world, 0.07, 0.15)));

        when(assetRepository.findBySymbolAndMicCode("URTH", "ARCX")).thenReturn(Optional.of(world));
        when(assetRepository.findBySymbolAndMicCode("IEMG", "ARCX")).thenReturn(Optional.of(emergingMarkets));
        when(estimator.estimate(anyList())).thenReturn(estimation);
        when(simulationService.ensureSeed(any())).thenAnswer(inv -> {
            SimulationParameters p = inv.getArgument(0);
            return p.seed() != null ? p : p.withSeed(731L);
        });
        when(simulationService.run(anyList(), any(), any())).thenReturn(run);
    }

    private static SimulationParameters params(int paths, Long seed) {
        return new SimulationParameters(10_000.0, 200.0, 20, paths, seed);
    }

    @SuppressWarnings("unchecked")
    private List<WeightedAsset> capturedWeights() {
        ArgumentCaptor<List<WeightedAsset>> captor = ArgumentCaptor.forClass(List.class);
        verify(simulationService).run(captor.capture(), any(), any());
        return captor.getValue();
    }

    @Test
    void weltportfolioWirdMitVorhandenerAnlageSimuliert() {
        SimulationResult result = service.simulate(900L, params(10_000, 42L));

        verify(estimator).estimate(List.of(world));
        verify(assetRepository, never()).save(any());
        List<WeightedAsset> weights = capturedWeights();
        assertThat(weights).hasSize(1);
        assertThat(weights.get(0).asset()).isEqualTo(world);
        assertThat(weights.get(0).share()).isEqualByComparingTo("1");

        assertThat(result.portfolioId()).isEqualTo(900L);
        assertThat(result.estimation()).isEqualTo(estimation);
        assertThat(result.timeline()).isEqualTo(run.timeline());
        assertThat(result.metrics()).isEqualTo(run.metrics());
        assertThat(result.runtimeMs()).isGreaterThanOrEqualTo(0);
        // Erfolgreiche Aktion wird als Nutzungsereignis erfasst (E37)
        verify(usageEventRecorder).record(UsageEventType.DEMO_SIMULATION_RUN);
    }

    @Test
    void fehlendeAnlageWirdAngelegt() {
        when(assetRepository.findBySymbolAndMicCode("URTH", "ARCX")).thenReturn(Optional.empty());
        when(assetRepository.save(any(Asset.class))).thenReturn(world);

        service.simulate(900L, params(10_000, 42L));

        ArgumentCaptor<Asset> captor = ArgumentCaptor.forClass(Asset.class);
        verify(assetRepository).save(captor.capture());
        assertThat(captor.getValue().getSymbol()).isEqualTo("URTH");
        assertThat(captor.getValue().getMicCode()).isEqualTo("ARCX");
        verify(estimator).estimate(List.of(world));
    }

    @Test
    void portfolio901HatZweiGewichte() {
        service.simulate(901L, params(10_000, 42L));

        List<WeightedAsset> weights = capturedWeights();
        assertThat(weights).extracting(WeightedAsset::asset).containsExactly(world, emergingMarkets);
        assertThat(weights.get(0).share()).isEqualByComparingTo(new BigDecimal("0.7"));
        assertThat(weights.get(1).share()).isEqualByComparingTo(new BigDecimal("0.3"));
    }

    @Test
    void unbekanntesBeispielportfolioLiefert404() {
        assertThatThrownBy(() -> service.simulate(5L, params(10_000, 42L)))
                .isInstanceOf(NotFoundException.class)
                .hasMessage(Messages.DEMO_PORTFOLIO_NOT_FOUND);
        verifyNoInteractions(estimator);
        // Abgelehnte oder fehlgeschlagene Aktion zählt nicht (E37)
        verify(usageEventRecorder, never()).record(any());
    }

    @Test
    void zehntausendPfadeSindErlaubt() {
        assertThat(service.simulate(900L, params(10_000, 42L)).parameters().paths()).isEqualTo(10_000);
    }

    @Test
    void mehrAlsZehntausendPfadeWerdenAbgelehnt() {
        assertThatThrownBy(() -> service.simulate(900L, params(10_001, 42L)))
                .isInstanceOfSatisfying(ValidationException.class, e -> {
                    assertThat(e.getMessage()).isEqualTo(Messages.DEMO_PATHS_INVALID);
                    assertThat(e.getErrors()).extracting(ValidationException.FieldError::field).containsExactly("paths");
                });
        verifyNoInteractions(estimator);
    }

    @Test
    void ohneSeedWirdEinerErzeugt() {
        SimulationResult result = service.simulate(900L, params(10_000, null));

        verify(simulationService).ensureSeed(params(10_000, null));
        assertThat(result.parameters().seed()).isEqualTo(731L);
    }

    @Test
    void ohnePortfolioIdWirdAbgelehnt() {
        assertThatThrownBy(() -> service.simulate(null, params(10_000, 42L)))
                .isInstanceOf(ValidationException.class)
                .hasMessage(Messages.PORTFOLIO_ID_REQUIRED);
        verifyNoInteractions(estimator);
    }

    @Test
    void ohneParameterWirdAbgelehnt() {
        assertThatThrownBy(() -> service.simulate(900L, null))
                .isInstanceOf(ValidationException.class)
                .hasMessage(Messages.PARAMETERS_REQUIRED);
        verifyNoInteractions(estimator);
    }
}
