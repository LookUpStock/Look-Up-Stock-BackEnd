package com.lookupstock.backend.graphql;

import java.util.List;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.ContextValue;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

import com.lookupstock.backend.dto.AssetResponse;
import com.lookupstock.backend.dto.PortfolioResponse;
import com.lookupstock.backend.dto.SavedSimulationListResponse;
import com.lookupstock.backend.exception.Messages;
import com.lookupstock.backend.exception.UnauthorizedException;
import com.lookupstock.backend.model.Metrics;
import com.lookupstock.backend.model.SavedSimulation;
import com.lookupstock.backend.model.User;
import com.lookupstock.backend.service.AssetService;
import com.lookupstock.backend.service.AuthService;
import com.lookupstock.backend.service.PortfolioService;
import com.lookupstock.backend.service.SavedSimulationService;

/**
 * Lesende GraphQL-Abfragen (BE #94). Übersetzt nur; die Fachlogik liegt in denselben Services wie bei REST (ADR-02).
 */
@Controller
public class QueryController {

    private final AssetService assetService;
    private final PortfolioService portfolioService;
    private final SavedSimulationService savedSimulationService;
    private final AuthService authService;

    public QueryController(AssetService assetService, PortfolioService portfolioService,
                           SavedSimulationService savedSimulationService, AuthService authService) {
        this.assetService = assetService;
        this.portfolioService = portfolioService;
        this.savedSimulationService = savedSimulationService;
        this.authService = authService;
    }

    // Anlagensuche ohne Anmeldung; Prüfregeln und Cache wie bei GET /assets/search
    @QueryMapping
    public List<AssetResponse> searchAssets(@Argument String query, @Argument Integer limit) {
        return assetService.search(query, limit).stream().map(AssetResponse::from).toList();
    }

    @QueryMapping
    public List<PortfolioResponse> portfolios(
            @ContextValue(name = AuthTokenInterceptor.TOKEN_KEY, required = false) String token) {
        User owner = currentUser(token);
        return portfolioService.findAll(owner).stream().map(PortfolioResponse::from).toList();
    }

    @QueryMapping
    public PortfolioResponse portfolio(@Argument Long id,
            @ContextValue(name = AuthTokenInterceptor.TOKEN_KEY, required = false) String token) {
        return PortfolioResponse.from(portfolioService.findById(id, currentUser(token)));
    }

    @QueryMapping
    public List<SavedSimulationListResponse.Entry> savedSimulations(
            @ContextValue(name = AuthTokenInterceptor.TOKEN_KEY, required = false) String token) {
        User owner = currentUser(token);
        return savedSimulationService.findAll(owner).stream().map(SavedSimulationListResponse.Entry::from).toList();
    }

    @QueryMapping
    public SavedSimulationListResponse.Entry savedSimulation(@Argument Long id,
            @ContextValue(name = AuthTokenInterceptor.TOKEN_KEY, required = false) String token) {
        return SavedSimulationListResponse.Entry.from(savedSimulationService.findById(id, currentUser(token)));
    }

    // Wird nur aufgerufen, wenn die Abfrage SavedSimulation.portfolio anfordert
    @SchemaMapping(typeName = "SavedSimulation", field = "portfolio")
    public PortfolioResponse savedSimulationPortfolio(SavedSimulationListResponse.Entry simulation,
            @ContextValue(name = AuthTokenInterceptor.TOKEN_KEY, required = false) String token) {
        return PortfolioResponse.from(portfolioService.findById(simulation.portfolioId(), currentUser(token)));
    }

    // Wird nur aufgerufen, wenn die Abfrage SavedSimulation.metrics anfordert; liest das gespeicherte Ergebnis
    @SchemaMapping(typeName = "SavedSimulation", field = "metrics")
    public Metrics savedSimulationMetrics(SavedSimulationListResponse.Entry simulation,
            @ContextValue(name = AuthTokenInterceptor.TOKEN_KEY, required = false) String token) {
        SavedSimulation saved = savedSimulationService.findById(simulation.id(), currentUser(token));
        return savedSimulationService.readResult(saved).metrics();
    }

    // Ohne Token oder mit ungültigem Token: UNAUTHORIZED mit „Bitte melde dich an.“ wie bei REST
    private User currentUser(String token) {
        if (token == null) {
            throw new UnauthorizedException(Messages.NOT_LOGGED_IN);
        }
        return authService.authenticate(token);
    }
}