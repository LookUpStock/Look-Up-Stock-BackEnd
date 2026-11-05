package com.lookupstock.marktdaten.twelvedata;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TwelveDataErrorTest {

    @Test
    void ordnetCodesEin() {
        assertThat(TwelveDataError.fromCode(401)).isEqualTo(TwelveDataError.INVALID_KEY);
        assertThat(TwelveDataError.fromCode(403)).isEqualTo(TwelveDataError.PLAN_RESTRICTED);
        assertThat(TwelveDataError.fromCode(429)).isEqualTo(TwelveDataError.RATE_LIMIT);
        assertThat(TwelveDataError.fromCode(400)).isEqualTo(TwelveDataError.NOT_FOUND);
        assertThat(TwelveDataError.fromCode(404)).isEqualTo(TwelveDataError.NOT_FOUND);
        assertThat(TwelveDataError.fromCode(500)).isEqualTo(TwelveDataError.OTHER);
        assertThat(TwelveDataError.fromCode(null)).isEqualTo(TwelveDataError.OTHER);
    }
}
