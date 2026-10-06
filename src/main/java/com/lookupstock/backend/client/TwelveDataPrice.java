package com.lookupstock.backend.client;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonProperty;

record TwelveDataPrice(
        @JsonProperty("datetime") String datetime,
        @JsonProperty("close") BigDecimal close) {

    PricePoint toPricePoint() {
        return new PricePoint(LocalDate.parse(datetime.substring(0, 10)), close);
    }
}
