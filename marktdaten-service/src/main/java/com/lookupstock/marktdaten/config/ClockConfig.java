package com.lookupstock.marktdaten.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

    // Im Test wird eine feste Uhr übergeben, damit das Alter der Caches prüfbar ist
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
