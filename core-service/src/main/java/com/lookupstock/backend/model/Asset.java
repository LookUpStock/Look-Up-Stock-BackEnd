package com.lookupstock.backend.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "asset")

public class Asset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String symbol;
    private String isin;
    private String name;
    private String exchange;
    private String currency;
    private String type;
    private String micCode;

    protected Asset() {
    }

    public Asset(String symbol, String isin, String name, String exchange, String currency, String type, String micCode) {
        this.micCode = micCode;
        this.symbol = symbol;
        this.isin = isin;
        this.name = name;
        this.exchange = exchange;
        this.currency = currency;
        this.type = type;
    }

    public Long getId() {
        return id;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getIsin() {
        return isin;
    }

    public String getName() {
        return name;
    }

    public String getExchange() {
        return exchange;
    }

    public String getCurrency() {
        return currency;
    }

    public String getType() {
        return type;
    }

    public String getMicCode() {
        return micCode;
    }
}
