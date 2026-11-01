package com.lookupstock.marktdaten.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.lookupstock.marktdaten.model.PricePoint;

/**
 * Lesen und Schreiben des Kurs-Caches in der Tabelle marktdaten.price_cache (NF-07, Migration V1).
 * Schlüssel ist Symbol und MIC-Code; ohne MIC wird '' gespeichert.
 */
@Repository
public class PriceCacheRepository {

    private final JdbcTemplate jdbcTemplate;

    public PriceCacheRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Zwischengespeicherte Werte aufsteigend nach Datum, mit Abrufzeitpunkt. */
    public record CachedPrices(Instant fetchedAt, List<PricePoint> prices) {
    }

    private record Row(LocalDate date, BigDecimal close, Instant fetchedAt) {
    }

    @Transactional(readOnly = true)
    public Optional<CachedPrices> load(String symbol, String micCode) {
        List<Row> rows = jdbcTemplate.query("""
                SELECT price_date, close_price, fetched_at
                FROM marktdaten.price_cache
                WHERE symbol = ? AND mic_code = ?
                ORDER BY price_date
                """,
                (rs, rowNum) -> new Row(
                        rs.getObject("price_date", LocalDate.class),
                        rs.getBigDecimal("close_price"),
                        rs.getObject("fetched_at", OffsetDateTime.class).toInstant()),
                symbol, key(micCode));
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        // alle Zeilen stammen aus demselben Abruf; zur Sicherheit der älteste Zeitpunkt
        Instant fetchedAt = rows.stream().map(Row::fetchedAt).min(Comparator.naturalOrder()).orElseThrow();
        return Optional.of(new CachedPrices(fetchedAt,
                rows.stream().map(r -> new PricePoint(r.date(), r.close())).toList()));
    }

    /**
     * Ersetzt alle Werte von Symbol und MIC-Code durch einen neuen Abruf, in einer eigenen Transaktion:
     * Scheitert das Speichern (z. B. zwei gleichzeitige Abrufe), bleibt die Transaktion des Aufrufers intakt,
     * etwa die des Queue-Verbrauchers für kursdaten.vorladen.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void replace(String symbol, String micCode, List<PricePoint> prices, Instant fetchedAt) {
        String mic = key(micCode);
        jdbcTemplate.update("DELETE FROM marktdaten.price_cache WHERE symbol = ? AND mic_code = ?", symbol, mic);
        OffsetDateTime fetched = fetchedAt.atOffset(ZoneOffset.UTC);
        jdbcTemplate.batchUpdate("""
                INSERT INTO marktdaten.price_cache (symbol, mic_code, price_date, close_price, fetched_at)
                VALUES (?, ?, ?, ?, ?)
                """,
                prices, prices.size(), (ps, p) -> {
                    ps.setString(1, symbol);
                    ps.setString(2, mic);
                    ps.setObject(3, p.date());
                    ps.setBigDecimal(4, p.close());
                    ps.setObject(5, fetched);
                });
    }

    // null und leer bedeuten beide „Standardbörse des Anbieters“
    static String key(String micCode) {
        return micCode == null ? "" : micCode.trim();
    }
}
