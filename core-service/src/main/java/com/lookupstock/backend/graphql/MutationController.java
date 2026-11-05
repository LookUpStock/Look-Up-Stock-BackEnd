package com.lookupstock.backend.graphql;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.ContextValue;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.stereotype.Controller;

import com.lookupstock.backend.dto.ComparisonRequest;
import com.lookupstock.backend.dto.ComparisonResponse;
import com.lookupstock.backend.dto.PortfolioRequest;
import com.lookupstock.backend.dto.PortfolioResponse;
import com.lookupstock.backend.dto.SimulationRequest;
import com.lookupstock.backend.dto.SimulationResponse;
import com.lookupstock.backend.exception.Messages;
import com.lookupstock.backend.exception.UnauthorizedException;
import com.lookupstock.backend.model.SavedSimulation;
import com.lookupstock.backend.model.SimulationParameters;
import com.lookupstock.backend.model.SimulationResult;
import com.lookupstock.backend.model.User;
import com.lookupstock.backend.service.AuthService;
import com.lookupstock.backend.service.ComparisonService;
import com.lookupstock.backend.service.PortfolioService;
import com.lookupstock.backend.service.SavedSimulationService;
import com.lookupstock.backend.service.SimulationService;

@Controller
public class MutationController {

    private final PortfolioService portfolioService;
    private final SimulationService simulationService;
    private final SavedSimulationService savedSimulationService;
    private final ComparisonService comparisonService;
    private final AuthService authService;

    public MutationController(PortfolioService portfolioService, SimulationService simulationService,
                              SavedSimulationService savedSimulationService, ComparisonService comparisonService,
                              AuthService authService) {
        this.portfolioService = portfolioService;
        this.simulationService = simulationService;
        this.savedSimulationService = savedSimulationService;
        this.comparisonService = comparisonService;
        this.authService = authService;
    }

    @MutationMapping
    public PortfolioResponse createPortfolio(@Argument PortfolioRequest input,
            @ContextValue(name = AuthTokenInterceptor.TOKEN_KEY, required = false) String token) {
        User owner = currentUser(token);
        return PortfolioResponse.from(portfolioService.create(input, owner));
    }

    @MutationMapping
    public PortfolioResponse updatePortfolio(@Argument Long id, @Argument PortfolioRequest input,
            @ContextValue(name = AuthTokenInterceptor.TOKEN_KEY, required = false) String token) {
        User owner = currentUser(token);
        return PortfolioResponse.from(portfolioService.update(id, input, owner));
    }

    @MutationMapping
    public boolean deletePortfolio(@Argument Long id,
            @ContextValue(name = AuthTokenInterceptor.TOKEN_KEY, required = false) String token) {
        User owner = currentUser(token);
        portfolioService.delete(id, owner);
        return true;
    }

    @MutationMapping
    public SimulationResponse simulate(@Argument SimulationRequest input,
            @ContextValue(name = AuthTokenInterceptor.TOKEN_KEY, required = false) String token) {
        User user = currentUser(token);
        // Validate the name before computing so a missing name is reported immediately.
        String name = input.validatedName();
        SimulationParameters parameters = input.parameters() == null
                ? null
                : input.parameters().toSimulationParameters();
        // When saving, only SIMULATION_SAVED is counted; the preceding simulation was already counted.
        SimulationResult result = input.shouldSave()
                ? simulationService.simulateForSave(input.portfolioId(), parameters, user)
                : simulationService.simulate(input.portfolioId(), parameters, user);

        if (!input.shouldSave()) {
            return SimulationResponse.from(result);
        }
        SavedSimulation saved = savedSimulationService.save(result, name, user);
        return SimulationResponse.from(saved, savedSimulationService.readResult(saved));
    }

    @MutationMapping
    public boolean deleteSavedSimulation(@Argument Long id,
            @ContextValue(name = AuthTokenInterceptor.TOKEN_KEY, required = false) String token) {
        User user = currentUser(token);
        savedSimulationService.delete(id, user);
        return true;
    }

    @MutationMapping
    public ComparisonResponse compare(@Argument ComparisonRequest input,
            @ContextValue(name = AuthTokenInterceptor.TOKEN_KEY, required = false) String token) {
        User user = currentUser(token);
        SimulationParameters parameters = input.parameters() == null
                ? null
                : input.parameters().toSimulationParameters();
        return ComparisonResponse.from(comparisonService.compare(input.toEntries(), parameters, user));
    }

    private User currentUser(String token) {
        if (token == null) {
            throw new UnauthorizedException(Messages.NOT_LOGGED_IN);
        }
        return authService.authenticate(token);
    }
}
