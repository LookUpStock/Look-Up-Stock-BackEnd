package com.lookupstock.backend.graphql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.graphql.test.autoconfigure.GraphQlTest;
import org.springframework.context.annotation.Import;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.graphql.test.tester.ExecutionGraphQlServiceTester;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

import com.lookupstock.backend.dto.PortfolioRequest;
import com.lookupstock.backend.exception.InsufficientDataException;
import com.lookupstock.backend.exception.Messages;
import com.lookupstock.backend.exception.NotFoundException;
import com.lookupstock.backend.model.Asset;
import com.lookupstock.backend.model.ComparisonEntry;
import com.lookupstock.backend.model.ComparisonResult;
import com.lookupstock.backend.model.Estimate;
import com.lookupstock.backend.model.EstimationResult;
import com.lookupstock.backend.model.Metrics;
import com.lookupstock.backend.model.Portfolio;
import com.lookupstock.backend.model.SimulationParameters;
import com.lookupstock.backend.model.SimulationResult;
import com.lookupstock.backend.model.TimelinePoint;
import com.lookupstock.backend.model.User;
import com.lookupstock.backend.service.AuthService;
import com.lookupstock.backend.service.ComparisonService;
import com.lookupstock.backend.service.PortfolioService;
import com.lookupstock.backend.service.SavedSimulationService;
import com.lookupstock.backend.service.SimulationService;

/** BE #108: schreibende GraphQL-Operationen mit denselben Regeln und Fehlern wie bei REST. */
@GraphQlTest(MutationController.class)
@Import(GraphQlExceptionResolver.class)
class MutationControllerTest {

    private static final User OWNER = new User("anna@example.com", "hash");
    private static final String VALID_TOKEN = "valid";

    @Autowired
    private ExecutionGraphQlServiceTester graphQlTester;

    @MockitoBean
    private PortfolioService portfolioService;

    @MockitoBean
    private SimulationService simulationService;

    @MockitoBean
    private SavedSimulationService savedSimulationService;

    @MockitoBean
    private ComparisonService comparisonService;

    @MockitoBean
    private AuthService authService;

    @BeforeEach
    void setUp() {
        when(authService.authenticate(VALID_TOKEN)).thenReturn(OWNER);
    }

    // Token so in den Kontext legen, wie es der AuthTokenInterceptor bei einer HTTP-Anfrage tut
    private GraphQlTester loggedIn() {
        return graphQlTester.mutate()
                .configureExecutionInput((input, builder) -> builder
                        .graphQLContext(Map.<String, Object>of(AuthTokenInterceptor.TOKEN_KEY, VALID_TOKEN))
                        .build())
                .build();
    }

    // ---------- Portfolios ----------

    @Test
    void createPortfolioWithoutTokenReturnsUnauthorized() {
        graphQlTester.document("mutation { createPortfolio(input: {name: \"X\", positions: []}) { id } }")
                .execute()
                .errors()
                .satisfy(errors -> assertThat(errors).singleElement().satisfies(error -> {
                    assertThat(error.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
                    assertThat(error.getMessage()).isEqualTo(Messages.NOT_LOGGED_IN);
                }));
        verifyNoInteractions(portfolioService);
    }

    @Test
    void createPortfolioBindsInputLikeRestBody() {
        when(portfolioService.create(any(), eq(OWNER))).thenReturn(samplePortfolio());

        loggedIn().document("""
                mutation {
                  createPortfolio(input: {name: "Mein Weltportfolio",
                                          positions: [{assetId: 1, weight: 66.67}, {assetId: "2", weight: 33.33}]}) {
                    id name positions { symbol weight }
                  }
                }""")
                .execute()
                .path("createPortfolio.name").entity(String.class).isEqualTo("Mein Weltportfolio")
                .path("createPortfolio.positions[0].symbol").entity(String.class).isEqualTo("URTH");

        ArgumentCaptor<PortfolioRequest> captor = ArgumentCaptor.forClass(PortfolioRequest.class);
        verify(portfolioService).create(captor.capture(), eq(OWNER));
        PortfolioRequest request = captor.getValue();
        assertThat(request.name()).isEqualTo("Mein Weltportfolio");
        assertThat(request.positions()).hasSize(2);
        assertThat(request.positions().get(0).assetId()).isEqualTo(1L);
        assertThat(request.positions().get(1).assetId()).isEqualTo(2L);
        assertThat(request.positions().get(1).weight()).isEqualByComparingTo(new BigDecimal("33.33"));
    }

    @Test
    void updateForeignPortfolioReturnsNotFound() {
        // NF-04: fremde ID wie nicht vorhanden
        when(portfolioService.update(eq(99L), any(), eq(OWNER)))
                .thenThrow(new NotFoundException(Messages.PORTFOLIO_NOT_FOUND));

        loggedIn().document("mutation { updatePortfolio(id: 99, input: {name: \"Neu\", positions: []}) { id } }")
                .execute()
                .errors()
                .satisfy(errors -> assertThat(errors).singleElement().satisfies(error -> {
                    assertThat(error.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
                    assertThat(error.getMessage()).isEqualTo(Messages.PORTFOLIO_NOT_FOUND);
                }));
    }

    @Test
    void deletePortfolioReturnsTrue() {
        loggedIn().document("mutation { deletePortfolio(id: 5) }")
                .execute()
                .path("deletePortfolio").entity(Boolean.class).isEqualTo(true);
        verify(portfolioService).delete(5L, OWNER);
    }

    @Test
    void deleteForeignPortfolioReturnsNotFound() {
        doThrow(new NotFoundException(Messages.PORTFOLIO_NOT_FOUND)).when(portfolioService).delete(5L, OWNER);

        loggedIn().document("mutation { deletePortfolio(id: 5) }")
                .execute()
                .errors()
                .satisfy(errors -> assertThat(errors).singleElement()
                        .satisfies(error -> assertThat(error.getErrorType()).isEqualTo(ErrorType.NOT_FOUND)));
    }

    // ---------- Simulation ----------

    @Test
    void simulateWithoutSaveReturnsResultAndSavesNothing() {
        when(simulationService.simulate(eq(5L), any(), eq(OWNER))).thenReturn(sampleResult());

        loggedIn().document("""
                mutation {
                  simulate(input: {portfolioId: 5,
                                   parameters: {startCapital: 10000, monthlyContribution: 200, horizonYears: 20, seed: 42}}) {
                    id portfolioId parameters { horizonYears paths seed } metrics { medianEndValue } timeline { month p50 }
                  }
                }""")
                .execute()
                .path("simulate.id").valueIsNull()
                .path("simulate.portfolioId").entity(Long.class).isEqualTo(5L)
                .path("simulate.parameters.seed").entity(Double.class).isEqualTo(42.0)
                .path("simulate.metrics.medianEndValue").entity(Double.class).isEqualTo(120_000.0)
                .path("simulate.timeline[1].p50").entity(Double.class).isEqualTo(10_300.0);

        ArgumentCaptor<SimulationParameters> captor = ArgumentCaptor.forClass(SimulationParameters.class);
        verify(simulationService).simulate(eq(5L), captor.capture(), eq(OWNER));
        assertThat(captor.getValue().horizonYears()).isEqualTo(20);
        assertThat(captor.getValue().seed()).isEqualTo(42L);
        assertThat(captor.getValue().paths()).isEqualTo(SimulationParameters.DEFAULT_PATHS);
        verify(savedSimulationService, never()).save(any(), any(), any());
    }

    @Test
    void simulateWithSaveButWithoutNameIsRejectedBeforeCalculating() {
        loggedIn().document("""
                mutation {
                  simulate(input: {portfolioId: 5, save: true,
                                   parameters: {startCapital: 10000, monthlyContribution: 200, horizonYears: 20}}) { id }
                }""")
                .execute()
                .errors()
                .satisfy(errors -> assertThat(errors).singleElement().satisfies(error -> {
                    assertThat(error.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
                    assertThat(error.getMessage()).isEqualTo(Messages.NAME_INVALID);
                    assertThat(error.getExtensions()).containsEntry("code", "VALIDATION_ERROR");
                }));
        verifyNoInteractions(simulationService);
    }

    @Test
    void simulateWithTooShortHistoryReturnsInsufficientData() {
        Asset young = asset("NEWX", "Junger ETF", 8L);
        when(simulationService.simulate(eq(5L), any(), eq(OWNER)))
                .thenThrow(new InsufficientDataException(List.of(young)));

        loggedIn().document("""
                mutation {
                  simulate(input: {portfolioId: 5,
                                   parameters: {startCapital: 10000, monthlyContribution: 200, horizonYears: 20}}) { id }
                }""")
                .execute()
                .errors()
                .satisfy(errors -> assertThat(errors).singleElement().satisfies(error -> {
                    assertThat(error.getExtensions()).containsEntry("code", "INSUFFICIENT_DATA");
                    assertThat(error.getMessage()).contains("Junger ETF");
                }));
    }

    @Test
    void deleteSavedSimulationReturnsTrue() {
        loggedIn().document("mutation { deleteSavedSimulation(id: 12) }")
                .execute()
                .path("deleteSavedSimulation").entity(Boolean.class).isEqualTo(true);
        verify(savedSimulationService).delete(12L, OWNER);
    }

    // ---------- Vergleich ----------

    @Test
    void compareReturnsItemsAndPairwise() {
        when(comparisonService.compare(any(), any(), eq(OWNER))).thenReturn(sampleComparison());

        loggedIn().document("""
                mutation {
                  compare(input: {items: [{label: "Welt", portfolioId: 5}, {assetId: 1}],
                                  parameters: {startCapital: 10000, monthlyContribution: 200, horizonYears: 20, seed: 7}}) {
                    results { label portfolioId assetId metrics { medianEndValue } }
                    pairwise { a b probabilityAOutperformsB }
                    estimates { historyFrom historyTo }
                  }
                }""")
                .execute()
                .path("compare.results[0].label").entity(String.class).isEqualTo("Welt")
                .path("compare.results[1].assetId").entity(Long.class).isEqualTo(1L)
                .path("compare.pairwise[0].probabilityAOutperformsB").entity(Double.class).isEqualTo(0.62)
                .path("compare.estimates.historyFrom").entity(String.class).isEqualTo("2016-10-01");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ComparisonEntry>> captor = ArgumentCaptor.forClass(List.class);
        verify(comparisonService).compare(captor.capture(), any(), eq(OWNER));
        assertThat(captor.getValue()).containsExactly(
                new ComparisonEntry("Welt", 5L, null), new ComparisonEntry(null, null, 1L));
    }

    // ---------- Testdaten ----------

    private static Asset asset(String symbol, String name, Long id) {
        Asset asset = new Asset(symbol, null, name, "NYSE", "USD", "ETF", "ARCX");
        ReflectionTestUtils.setField(asset, "id", id);
        return asset;
    }

    private static Portfolio samplePortfolio() {
        Portfolio portfolio = new Portfolio("Mein Weltportfolio", OWNER);
        portfolio.addPosition(asset("URTH", "iShares MSCI World ETF", 1L), new BigDecimal("100"));
        ReflectionTestUtils.setField(portfolio, "id", 5L);
        Instant time = Instant.parse("2026-10-18T10:00:00Z");
        ReflectionTestUtils.setField(portfolio, "createdAt", time);
        ReflectionTestUtils.setField(portfolio, "updatedAt", time);
        return portfolio;
    }

    private static EstimationResult sampleEstimation() {
        return new EstimationResult(LocalDate.of(2016, 10, 1), LocalDate.of(2026, 9, 1),
                List.of(new Estimate(asset("URTH", "iShares MSCI World ETF", 1L), 0.078, 0.152)));
    }

    private static Metrics sampleMetrics() {
        return new Metrics(58_000, 120_000, 70_000, 200_000, 0, 0.31, null);
    }

    private static List<TimelinePoint> sampleTimeline() {
        return List.of(new TimelinePoint(0, 10_000, 10_000, 10_000), new TimelinePoint(1, 9_800, 10_300, 10_700));
    }

    private static SimulationResult sampleResult() {
        return new SimulationResult(5L, new SimulationParameters(10_000.0, 200.0, 20, 10_000, 42L),
                sampleEstimation(), sampleTimeline(), sampleMetrics(), 150L);
    }

    private static ComparisonResult sampleComparison() {
        return new ComparisonResult(new SimulationParameters(10_000.0, 200.0, 20, 10_000, 7L), sampleEstimation(),
                List.of(new ComparisonResult.Item("Welt", 5L, null, sampleTimeline(), sampleMetrics()),
                        new ComparisonResult.Item("iShares MSCI World ETF", null, 1L, sampleTimeline(), sampleMetrics())),
                List.of(new ComparisonResult.Pairwise(0, 1, 0.62)),
                300L, Instant.parse("2026-10-28T09:00:00Z"));
    }
}
