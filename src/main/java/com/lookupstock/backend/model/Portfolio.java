package com.lookupstock.backend.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import com.lookupstock.backend.exception.Messages;
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
    private Instant createdAt;
    private Instant updatedAt;

    @PrePersist
    void beimAnlegen() {
        createdAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        updatedAt = createdAt;
    }

    @PreUpdate
    void beimAendern() {
        updatedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<Position> getPositions() {
        return positions;
    }

    public void validate() {
        List<ValidationException.FieldError> errors = new ArrayList<>();

        if (name == null || name.isBlank() || name.length() > 100) {
            errors.add(new ValidationException.FieldError("name", Messages.NAME_INVALID));
        }

        if (positions.isEmpty()) {
            errors.add(new ValidationException.FieldError("positions", Messages.POSITIONS_EMPTY));
        }

        Set<Long> assetIds = new HashSet<>();
        boolean duplicateReported = false;
        for (Position p : positions) {
            // doppelte Anlagen nur einmal melden, der Text nennt kein Symbol
            if (!assetIds.add(p.getAsset().getId()) && !duplicateReported) {
                errors.add(new ValidationException.FieldError("positions", Messages.DUPLICATE_ASSET));
                duplicateReported = true;
            }
            BigDecimal w = p.getWeight();
            if (w == null || w.compareTo(BigDecimal.ZERO) <= 0) {
                errors.add(new ValidationException.FieldError("positions", Messages.WEIGHT_NOT_POSITIVE));
            } else if (w.stripTrailingZeros().scale() > 2) {
                errors.add(new ValidationException.FieldError("positions", Messages.WEIGHT_TOO_MANY_DECIMALS));
            }
        }

        BigDecimal summe = positions.stream()
                .map(Position::getWeight)
                .filter(w -> w != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (!positions.isEmpty() && summe.subtract(new BigDecimal("100")).abs().compareTo(new BigDecimal("0.01")) > 0) {
            errors.add(new ValidationException.FieldError("positions", Messages.WEIGHT_SUM));
            errors.add(new ValidationException.FieldError("positions", Messages.weightSumDetail(summe)));
        }

        if (!errors.isEmpty()) {
            throw new ValidationException(errors.get(0).message(), errors);
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
