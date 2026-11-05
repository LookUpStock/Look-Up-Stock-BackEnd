package com.lookupstock.backend.model;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "position")
public class Position {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "portfolio_id")
    private Portfolio portfolio;

    @ManyToOne
    @JoinColumn(name = "asset_id")
    private Asset asset;

    @Column(precision = 5, scale = 2)
    private BigDecimal weight;

    

    protected Position() {
    }

    public Position(Portfolio portfolio, Asset asset, BigDecimal weight) {
        this.portfolio = portfolio;
        this.asset = asset;
        this.weight = weight;
    }

    public Long getId() {
        return id;
    }
    public Asset getAsset() {
        return asset;
    }
    public BigDecimal getWeight() {
        return weight;
    }

    void setWeight(BigDecimal weight) {
        this.weight = weight;
    }
}