package com.lookupstock.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Schaltet zeitgesteuerte Methoden (@Scheduled) ein, z. B. die Verbraucher der Queue (BE #114, ADR-07).
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}