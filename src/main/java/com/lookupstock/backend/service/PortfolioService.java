package com.lookupstock.backend.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lookupstock.backend.dto.PortfolioRequest;
import com.lookupstock.backend.dto.PositionRequest;
import com.lookupstock.backend.exception.Messages;
import com.lookupstock.backend.exception.NotFoundException;
import com.lookupstock.backend.exception.ValidationException;
import com.lookupstock.backend.model.Asset;
import com.lookupstock.backend.model.Portfolio;
import com.lookupstock.backend.model.User;
import com.lookupstock.backend.repository.AssetRepository;
import com.lookupstock.backend.repository.PortfolioRepository;

@Service
public class PortfolioService {

    private final PortfolioRepository portfolioRepository;
    private final AssetRepository assetRepository;

    public PortfolioService(PortfolioRepository portfolioRepository, AssetRepository assetRepository) {
        this.portfolioRepository = portfolioRepository;
        this.assetRepository = assetRepository;
    }

    @Transactional
    public Portfolio create(PortfolioRequest request, User owner) {
        List<PositionRequest> positions = request.positions() == null ? List.of() : request.positions();
        List<Long> ids = positions.stream().map(PositionRequest::assetId).filter(id -> id != null).toList();
        Map<Long, Asset> assets = assetRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Asset::getId, a -> a));
        List<ValidationException.FieldError> errors = new ArrayList<>();
        for (PositionRequest p : positions) {
            if (p.assetId() == null || !assets.containsKey(p.assetId())) {
                errors.add(new ValidationException.FieldError("positions", Messages.unknownAssetDetail(p.assetId())));
            }
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(Messages.ASSET_NOT_FOUND, errors);
        }
        Portfolio portfolio = new Portfolio(request.name(), owner);
        for (PositionRequest p : positions) {
            portfolio.addPosition(assets.get(p.assetId()), p.weight());
        }
        portfolio.validate();
        return portfolioRepository.save(portfolio);
    }

    @Transactional(readOnly = true)
    public List<Portfolio> findAll(User owner) {
        return portfolioRepository.findAllByOwnerOrderByCreatedAtDesc(owner);
    }

    @Transactional(readOnly = true)
    public Portfolio findById(Long id, User owner) {
        // fremdes Portfolio = nicht vorhanden (NF-04)
        return portfolioRepository.findByIdAndOwner(id, owner)
                .orElseThrow(() -> new NotFoundException(Messages.PORTFOLIO_NOT_FOUND));
    }
}
