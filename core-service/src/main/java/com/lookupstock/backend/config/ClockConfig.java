package com.lookupstock.backend.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

    // Im Test wird eine feste Uhr übergeben, damit Ablaufzeiten prüfbar sind
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}