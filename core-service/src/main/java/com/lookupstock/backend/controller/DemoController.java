package com.lookupstock.backend.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lookupstock.backend.dto.DemoPortfolioListResponse;
import com.lookupstock.backend.dto.DemoSimulationRequest;
import com.lookupstock.backend.dto.SimulationResponse;
import com.lookupstock.backend.model.SimulationParameters;
import com.lookupstock.backend.service.DemoService;

@RestController
@RequestMapping("/api/v1/demo")
public class DemoController {

    private final DemoService demoService;

    public DemoController(DemoService demoService) {
        this.demoService = demoService;
    }

    @GetMapping("/portfolios")
    public DemoPortfolioListResponse portfolios() {
        return DemoPortfolioListResponse.from(demoService.portfolios());
    }

    @PostMapping("/simulations")
    public SimulationResponse simulate(@RequestBody DemoSimulationRequest request) {
        SimulationParameters parameters = request.parameters() == null
                ? null
                : request.parameters().toSimulationParameters();
        return SimulationResponse.from(demoService.simulate(request.portfolioId(), parameters));
    }
}
