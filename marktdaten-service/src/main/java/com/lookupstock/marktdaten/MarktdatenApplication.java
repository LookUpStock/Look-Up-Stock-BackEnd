package com.lookupstock.marktdaten;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * marktdaten-service (ADR-07): kapselt Twelve Data samt Such- und Kurs-Cache.
 * Wird nur vom core-service aufgerufen, nie direkt vom Frontend.
 */
@SpringBootApplication
public class MarktdatenApplication {

    public static void main(String[] args) {
        SpringApplication.run(MarktdatenApplication.class, args);
    }
}
