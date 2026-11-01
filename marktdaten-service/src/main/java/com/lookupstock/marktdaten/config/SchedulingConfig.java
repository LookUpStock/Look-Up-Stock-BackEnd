package com.lookupstock.marktdaten.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Schaltet zeitgesteuerte Methoden ein, hier den Verbraucher für kursdaten.vorladen (ADR-07). */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
