package com.lookupstock.backend.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Ein zwischengespeicherter Monatswert einer Anlage (Tabelle price_history, Migration V6).
 * fetchedAt ist der Zeitpunkt des Abrufs bei Twelve Data; daran wird das Alter des Caches gemessen (NF-07).
 */
@Entity
@Table(name = "price_history")
public class PriceHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    @Column(name = "price_date", nullable = false)
    private LocalDate priceDate;

    @Column(name = "close_price", nullable = false, precision = 19, scale = 6)
    private BigDecimal closePrice;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    protected PriceHistory() {
        // für JPA
    }

    public PriceHistory(Asset asset, LocalDate priceDate, BigDecimal closePrice, Instant fetchedAt) {
        this.asset = asset;
        this.priceDate = priceDate;
        this.closePrice = closePrice;
        this.fetchedAt = fetchedAt;
    }

    public Long getId() { return id; }
    public Asset getAsset() { return asset; }
    public LocalDate getPriceDate() { return priceDate; }
    public BigDecimal getClosePrice() { return closePrice; }
    public Instant getFetchedAt() { return fetchedAt; }
}
