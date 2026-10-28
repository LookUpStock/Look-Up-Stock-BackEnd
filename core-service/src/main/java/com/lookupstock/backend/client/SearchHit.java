package com.lookupstock.backend.client;

public record SearchHit(String symbol, String name, String exchange,
                        String micCode, String currency, String type) {}