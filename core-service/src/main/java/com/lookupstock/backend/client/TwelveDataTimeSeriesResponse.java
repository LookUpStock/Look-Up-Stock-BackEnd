package com.lookupstock.backend.client;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

record TwelveDataTimeSeriesResponse(@JsonProperty("values") List<TwelveDataPrice> values, String status,
        Integer code, String message) {}
