package com.lookupstock.backend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lookupstock.backend.dto.PortfolioRequest;
import com.lookupstock.backend.dto.PortfolioResponse;
import com.lookupstock.backend.dto.PortfolioResponse.PortfolioListResponse;
import com.lookupstock.backend.model.Portfolio;
import com.lookupstock.backend.model.User;
import com.lookupstock.backend.service.PortfolioService;

@RestController
@RequestMapping("/api/v1/portfolios")
public class PortfolioController {

    private final PortfolioService portfolioService;

    public PortfolioController(PortfolioService portfolioService) {
        this.portfolioService = portfolioService;
    }

    @PostMapping
    public ResponseEntity<PortfolioResponse> create(@CurrentUser User owner, @RequestBody PortfolioRequest request) {
        Portfolio portfolio = portfolioService.create(request, owner);
        return ResponseEntity.status(HttpStatus.CREATED).body(PortfolioResponse.from(portfolio));
    }

    @GetMapping
    public PortfolioListResponse list(@CurrentUser User owner) {
        return new PortfolioListResponse(
                portfolioService.findAll(owner).stream().map(PortfolioResponse::from).toList());
    }

    @GetMapping("/{portfolioId}")
    public PortfolioResponse details(@CurrentUser User owner, @PathVariable Long portfolioId) {
        return PortfolioResponse.from(portfolioService.findById(portfolioId, owner));
    }

    // US-06: Portfolio ändern; Regeln wie beim Anlegen
    @PutMapping("/{portfolioId}")
    public PortfolioResponse update(@CurrentUser User owner, @PathVariable Long portfolioId,
            @RequestBody PortfolioRequest request) {
        return PortfolioResponse.from(portfolioService.update(portfolioId, request, owner));
    }

    // US-06: Portfolio löschen; 204 ohne Body
    @DeleteMapping("/{portfolioId}")
    public ResponseEntity<Void> delete(@CurrentUser User owner, @PathVariable Long portfolioId) {
        portfolioService.delete(portfolioId, owner);
        return ResponseEntity.noContent().build();
    }
}
