-- BE #106 (ADR-07): Der Kurs-Cache liegt jetzt im Schema marktdaten des marktdaten-service
-- (Tabelle marktdaten.price_cache). Die alte Tabelle des core-service wird nicht mehr verwendet.
-- Erst nach V9 (BE #110) und V10 (BE #114) mergen, damit die Reihenfolge der Migrationen stimmt.
DROP TABLE IF EXISTS price_history;
