package com.lookupstock.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.lookupstock.backend.model.SavedSimulation;
import com.lookupstock.backend.model.User;

public interface SavedSimulationRepository extends JpaRepository<SavedSimulation, Long> {

    List<SavedSimulation> findAllByOwnerOrderByCreatedAtDesc(User owner);

    Optional<SavedSimulation> findByIdAndOwner(Long id, User owner);
}