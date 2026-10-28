package com.lookupstock.marktdaten.twelvedata;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.lookupstock.marktdaten.model.PricePoint;

record TwelveDataPrice(
        @JsonProperty("datetime") String datetime,
        @JsonProperty("close") BigDecimal close) {

    PricePoint toPricePoint() {
        return new PricePoint(LocalDate.parse(datetime.substring(0, 10)), close);
    }
}
