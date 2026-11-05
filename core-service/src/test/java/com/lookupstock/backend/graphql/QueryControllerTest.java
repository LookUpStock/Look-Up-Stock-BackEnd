package com.lookupstock.backend.graphql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.graphql.test.autoconfigure.GraphQlTest;
import org.springframework.context.annotation.Import;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.graphql.test.tester.ExecutionGraphQlServiceTester;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

import com.lookupstock.backend.dto.StoredSimulationResult;
import com.lookupstock.backend.exception.Messages;
import com.lookupstock.backend.exception.NotFoundException;
import com.lookupstock.backend.model.Asset;
import com.lookupstock.backend.model.Metrics;
import com.lookupstock.backend.model.Portfolio;
import com.lookupstock.backend.model.SavedSimulation;
import com.lookupstock.backend.model.User;
import com.lookupstock.backend.service.AssetService;
import com.lookupstock.backend.service.AuthService;
import com.lookupstock.backend.service.PortfolioService;
import com.lookupstock.backend.service.SavedSimulationService;

@GraphQlTest(QueryController.class)
@Import(GraphQlExceptionResolver.class)
class QueryControllerTest {

    private static final User OWNER = new User("anna@example.com", "hash");
    private static final String VALID_TOKEN = "valid";

    @Autowired
    private ExecutionGraphQlServiceTester graphQlTester;

    @MockitoBean
    private AssetService assetService;

    @MockitoBean
    private PortfolioService portfolioService;

    @MockitoBean
    private SavedSimulationService savedSimulationService;

    @MockitoBean
    private AuthService authService;

    @BeforeEach
    void setUp() {
        when(authService.authenticate(VALID_TOKEN)).thenReturn(OWNER);
    }

    private GraphQlTester loggedIn() {
        return graphQlTester.mutate()
                .configureExecutionInput((input, builder) -> builder
                        .graphQLContext(Map.<String, Object>of(AuthTokenInterceptor.TOKEN_KEY, VALID_TOKEN))
                        .build())
                .build();
    }

    @Test
    void searchAssetsWorksWithoutLogin() {
        Asset etha = asset("ETHA", "iShares Ethereum Trust", 3L);
        when(assetService.search("etha", null)).thenReturn(List.of(etha));

        graphQlTester.document("{ searchAssets(query: \"etha\") { id symbol name } }")
                .execute()
                .path("searchAssets[0].symbol").entity(String.class).isEqualTo("ETHA")
                .path("searchAssets[0].id").entity(Long.class).isEqualTo(3L);
    }

    @Test
    void portfoliosWithoutTokenReturnsUnauthorized() {
        graphQlTester.document("{ portfolios { id } }")
                .execute()
                .errors()
                .satisfy(errors -> assertThat(errors).singleElement().satisfies(error -> {
                    assertThat(error.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
                    assertThat(error.getMessage()).isEqualTo(Messages.NOT_LOGGED_IN);
                }));
        verifyNoInteractions(portfolioService);
    }

    @Test
    void portfoliosReturnsOwnPortfoliosWithPositions() {
        when(portfolioService.findAll(OWNER)).thenReturn(List.of(samplePortfolio()));

        loggedIn().document("{ portfolios { id name positions { symbol weight } } }")
                .execute()
                .path("portfolios[0].name").entity(String.class).isEqualTo("Mein Weltportfolio")
                .path("portfolios[0].positions[0].symbol").entity(String.class).isEqualTo("URTH")
                .path("portfolios[0].positions[0].weight").entity(Double.class).isEqualTo(100.0);
    }

    @Test
    void foreignPortfolioReturnsNotFound() {
        when(portfolioService.findById(99L, OWNER)).thenThrow(new NotFoundException(Messages.PORTFOLIO_NOT_FOUND));

        loggedIn().document("{ portfolio(id: 99) { id } }")
                .execute()
                .errors()
                .satisfy(errors -> assertThat(errors).singleElement().satisfies(error -> {
                    assertThat(error.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
                    assertThat(error.getMessage()).isEqualTo(Messages.PORTFOLIO_NOT_FOUND);
                }));
    }

    @Test
    void metricsAreOnlyLoadedWhenRequested() {
        SavedSimulation saved = sampleSaved();
        when(savedSimulationService.findAll(OWNER)).thenReturn(List.of(saved));

        loggedIn().document("{ savedSimulations { id name } }")
                .execute()
                .path("savedSimulations[0].name").entity(String.class).isEqualTo("Sparplan 20 Jahre");

        verify(savedSimulationService, never()).readResult(any());
    }

    @Test
    void metricsAreLoadedFromStoredResult() {
        SavedSimulation saved = sampleSaved();
        Metrics metrics = new Metrics(58_000, 120_000, 70_000, 200_000, 0, 0.31, 0.85);
        when(savedSimulationService.findAll(OWNER)).thenReturn(List.of(saved));
        when(savedSimulationService.findById(12L, OWNER)).thenReturn(saved);
        when(savedSimulationService.readResult(saved)).thenReturn(new StoredSimulationResult(null, List.of(), metrics));

        loggedIn().document("{ savedSimulations { metrics { medianEndValue targetProbability } } }")
                .execute()
                .path("savedSimulations[0].metrics.medianEndValue").entity(Double.class).isEqualTo(120_000.0)
                .path("savedSimulations[0].metrics.targetProbability").entity(Double.class).isEqualTo(0.85);
    }

    private static Asset asset(String symbol, String name, Long id) {
        Asset asset = new Asset(symbol, null, name, "NASDAQ", "USD", "ETF", "XNAS");
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

    private static SavedSimulation sampleSaved() {
        Portfolio portfolio = samplePortfolio();
        SavedSimulation saved = new SavedSimulation(OWNER, portfolio, "Sparplan 20 Jahre",
                BigDecimal.valueOf(10_000), BigDecimal.valueOf(200), 20, 10_000, 42L, null, "{}", 123L);
        ReflectionTestUtils.setField(saved, "id", 12L);
        ReflectionTestUtils.setField(saved, "createdAt", Instant.parse("2026-10-18T10:00:00Z"));
        return saved;
    }
}