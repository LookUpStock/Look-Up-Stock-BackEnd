package com.lookupstock.backend.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.lookupstock.backend.exception.ValidationException;
import java.util.HashSet;
import java.util.Set;
import java.util.Comparator;

@Entity
@Table(name = "portfolio")
public class Portfolio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    void beimAnlegen() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void beimAendern() {
        updatedAt = LocalDateTime.now();
    }

    @OneToMany(mappedBy = "portfolio", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Position> positions = new ArrayList<>();

    protected Portfolio() {
    }

    public Portfolio(String name) {
        this.name = name;
    }

    public void addPosition(Asset asset, BigDecimal weight) {
        positions.add(new Position(this, asset, weight));
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public List<Position> getPositions() {
        return positions;
    }

    public void validate() {
        List<ValidationException.FieldError> errors = new ArrayList<>();

        if (name == null || name.isBlank()) {
            errors.add(new ValidationException.FieldError("name", "Bitte einen Namen eingeben."));
        } else if (name.length() > 100) {
            errors.add(new ValidationException.FieldError("name", "Der Name darf höchstens 100 Zeichen haben."));
        }

        if (positions.isEmpty()) {
            errors.add(new ValidationException.FieldError("positions", "Bitte mindestens eine Anlage hinzufügen."));
        }

        Set<Long> assetIds = new HashSet<>();
        for (Position p : positions) {
            if (!assetIds.add(p.getAsset().getId())) {
                errors.add(new ValidationException.FieldError("positions", "Die Anlage " + p.getAsset().getSymbol() + " ist doppelt enthalten."));
            }
            BigDecimal w = p.getWeight();
            if (w == null || w.compareTo(BigDecimal.ZERO) <= 0) {
                errors.add(new ValidationException.FieldError("positions", "Jede Gewichtung muss größer als 0 % sein."));
            } else if (w.stripTrailingZeros().scale() > 2) {
                errors.add(new ValidationException.FieldError("positions", "Gewichtungen dürfen höchstens 2 Nachkommastellen haben."));
            }
        }

        BigDecimal summe = positions.stream()
                .map(Position::getWeight)
                .filter(w -> w != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (!positions.isEmpty() && summe.subtract(new BigDecimal("100")).abs().compareTo(new BigDecimal("0.01")) > 0) {
            errors.add(new ValidationException.FieldError("positions", "Summe der Gewichtungen ist " + summe.stripTrailingZeros().toPlainString() + " %."));
        }

        if (!errors.isEmpty()) {
            throw new ValidationException("Das Portfolio ist ungültig.", errors);
        }
    }

    public List<WeightedAsset> getWeights() {
        BigDecimal hundert = new BigDecimal("100");
        return positions.stream()
                .sorted(Comparator.comparing(p -> p.getAsset().getId()))
                .map(p -> new WeightedAsset(p.getAsset(), p.getWeight().divide(hundert)))
                .toList();
    }

}
