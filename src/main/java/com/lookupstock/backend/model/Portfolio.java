package com.lookupstock.backend.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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


}
