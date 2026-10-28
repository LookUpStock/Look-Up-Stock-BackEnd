package com.lookupstock.marktdaten.twelvedata;

import com.fasterxml.jackson.annotation.JsonProperty;

record TwelveDataSymbol(
        String symbol,
        @JsonProperty("instrument_name") String instrumentName,
        String exchange,
        @JsonProperty("mic_code") String micCode,
        String currency,
        @JsonProperty("instrument_type") String instrumentType) {}