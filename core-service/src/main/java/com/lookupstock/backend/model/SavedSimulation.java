package com.lookupstock.backend.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "simulation")
public class SavedSimulation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "portfolio_id", nullable = false)
    private Portfolio portfolio;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "start_capital", nullable = false, precision = 19, scale = 2)
    private BigDecimal startCapital;

    @Column(name = "monthly_contribution", nullable = false, precision = 19, scale = 2)
    private BigDecimal monthlyContribution;

    @Column(name = "horizon_years", nullable = false)
    private int horizonYears;

    @Column(nullable = false)
    private int paths;

    @Column(nullable = false)
    private long seed;

    @Column(name = "target_amount", precision = 19, scale = 2)
    private BigDecimal targetAmount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String result;

    @Column(name = "runtime_ms", nullable = false)
    private long runtimeMs;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected SavedSimulation() {
    }

    public SavedSimulation(User owner, Portfolio portfolio, String name,
                           BigDecimal startCapital, BigDecimal monthlyContribution,
                           int horizonYears, int paths, long seed, BigDecimal targetAmount,
                           String result, long runtimeMs) {
        this.owner = owner;
        this.portfolio = portfolio;
        this.name = name;
        this.startCapital = startCapital;
        this.monthlyContribution = monthlyContribution;
        this.horizonYears = horizonYears;
        this.paths = paths;
        this.seed = seed;
        this.targetAmount = targetAmount;
        this.result = result;
        this.runtimeMs = runtimeMs;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    }

    public Long getId() { return id; }
    public User getOwner() { return owner; }
    public Portfolio getPortfolio() { return portfolio; }
    public String getName() { return name; }
    public BigDecimal getStartCapital() { return startCapital; }
    public BigDecimal getMonthlyContribution() { return monthlyContribution; }
    public int getHorizonYears() { return horizonYears; }
    public int getPaths() { return paths; }
    public long getSeed() { return seed; }
    public BigDecimal getTargetAmount() { return targetAmount; }
    public String getResult() { return result; }
    public long getRuntimeMs() { return runtimeMs; }
    public Instant getCreatedAt() { return createdAt; }
}