package com.lookupstock.backend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lookupstock.backend.dto.PortfolioRequest;
import com.lookupstock.backend.dto.PortfolioResponse;
import com.lookupstock.backend.dto.PortfolioResponse.PortfolioListResponse;
import com.lookupstock.backend.dto.TokenRequest;
import com.lookupstock.backend.model.Portfolio;
import com.lookupstock.backend.service.PortfolioService;

@RestController
@RequestMapping("/api/v1/portfolios")
public class PortfolioController {

    private final PortfolioService portfolioService;

    public PortfolioController(PortfolioService portfolioService) {
        this.portfolioService = portfolioService;
    }

    @PostMapping
    public ResponseEntity<PortfolioResponse> create(@RequestBody PortfolioRequest request) {
        Portfolio portfolio = portfolioService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(PortfolioResponse.from(portfolio));
    }

    @PostMapping("/list")
    public PortfolioListResponse list(@RequestBody(required = false) TokenRequest request) {
        return new PortfolioListResponse(
                portfolioService.findAll().stream().map(PortfolioResponse::from).toList());
    }

    @PostMapping("/{portfolioId}/details")
    public PortfolioResponse details(@PathVariable Long portfolioId,
            @RequestBody(required = false) TokenRequest request) {
        return PortfolioResponse.from(portfolioService.findById(portfolioId));
    }
}
