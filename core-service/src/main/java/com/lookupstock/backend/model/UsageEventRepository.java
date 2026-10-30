package com.lookupstock.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.lookupstock.backend.model.UsageEvent;

/**
 * Zugriff auf die Nutzungsereignisse (US-11).
 * Abfragen für die Auswertung je Monat kommen mit BE #111 dazu.
 */
public interface UsageEventRepository extends JpaRepository<UsageEvent, Long> {
}