package com.lookupstock.backend.model;

public record Estimate(Asset asset, double annualReturn, double annualVolatility) {
}