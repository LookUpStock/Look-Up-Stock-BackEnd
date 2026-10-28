package com.lookupstock.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.lookupstock.backend.model.SavedSimulation;
import com.lookupstock.backend.model.User;

/**
 * Zugriff auf gespeicherte Simulationen. Alle Abfragen filtern nach dem Besitzer,
 * damit fremde Simulationen wie nicht vorhandene behandelt werden (NF-04, ADR-04).
 */
public interface SavedSimulationRepository extends JpaRepository<SavedSimulation, Long> {

    // Neueste zuerst, für POST /simulations/list
    List<SavedSimulation> findAllByOwnerOrderByCreatedAtDesc(User owner);

    // Für details und DELETE; leer bei unbekannter oder fremder ID → 404
    Optional<SavedSimulation> findByIdAndOwner(Long id, User owner);
}