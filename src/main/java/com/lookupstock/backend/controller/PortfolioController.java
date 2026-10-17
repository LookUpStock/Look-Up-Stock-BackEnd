package com.lookupstock.backend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lookupstock.backend.dto.PortfolioRequest;
import com.lookupstock.backend.dto.PortfolioResponse;
import com.lookupstock.backend.dto.PortfolioResponse.PortfolioListResponse;
import com.lookupstock.backend.dto.TokenRequest;
import com.lookupstock.backend.model.Portfolio;
import com.lookupstock.backend.model.User;
import com.lookupstock.backend.service.AuthService;
import com.lookupstock.backend.service.PortfolioService;

@RestController
@RequestMapping("/api/v1/portfolios")
public class PortfolioController {

    private final PortfolioService portfolioService;
    private final AuthService authService;

    public PortfolioController(PortfolioService portfolioService, AuthService authService) {
        this.portfolioService = portfolioService;
        this.authService = authService;
    }

    @PostMapping
    public ResponseEntity<PortfolioResponse> create(@RequestBody PortfolioRequest request) {
        User owner = authService.authenticate(request.token());
        Portfolio portfolio = portfolioService.create(request, owner);
        return ResponseEntity.status(HttpStatus.CREATED).body(PortfolioResponse.from(portfolio));
    }

    @PostMapping("/list")
    public PortfolioListResponse list(@RequestBody(required = false) TokenRequest request) {
        User owner = authService.authenticate(request == null ? null : request.token());
        return new PortfolioListResponse(
                portfolioService.findAll(owner).stream().map(PortfolioResponse::from).toList());
    }

    @PostMapping("/{portfolioId}/details")
    public PortfolioResponse details(@PathVariable Long portfolioId,
            @RequestBody(required = false) TokenRequest request) {
        User owner = authService.authenticate(request == null ? null : request.token());
        return PortfolioResponse.from(portfolioService.findById(portfolioId, owner));
    }

    // US-06: Portfolio ändern; Regeln wie beim Anlegen
    @PutMapping("/{portfolioId}")
    public PortfolioResponse update(@PathVariable Long portfolioId, @RequestBody PortfolioRequest request) {
        User owner = authService.authenticate(request.token());
        return PortfolioResponse.from(portfolioService.update(portfolioId, request, owner));
    }

    // US-06: Portfolio löschen; 204 ohne Body
    @DeleteMapping("/{portfolioId}")
    public ResponseEntity<Void> delete(@PathVariable Long portfolioId,
            @RequestBody(required = false) TokenRequest request) {
        User owner = authService.authenticate(request == null ? null : request.token());
        portfolioService.delete(portfolioId, owner);
        return ResponseEntity.noContent().build();
    }
}
