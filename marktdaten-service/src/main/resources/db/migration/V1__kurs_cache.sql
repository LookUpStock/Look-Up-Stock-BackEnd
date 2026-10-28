-- BE #106 (ADR-07): Kurs-Cache des marktdaten-service im eigenen Schema marktdaten.
-- Ersetzt die Tabelle public.price_history des core-service. Schlüssel ist Symbol und MIC-Code,
-- nicht die asset.id, weil der marktdaten-service keine Tabellen des core-service kennt.
-- mic_code ist '' für "Standardbörse des Anbieters", damit die Eindeutigkeit auch ohne MIC greift.
CREATE TABLE price_cache (
    id          BIGSERIAL                PRIMARY KEY,
    symbol      VARCHAR(50)              NOT NULL,
    mic_code    VARCHAR(50)              NOT NULL DEFAULT '',
    price_date  DATE                     NOT NULL,
    close_price NUMERIC(19, 6)           NOT NULL,
    fetched_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_price_cache_symbol_mic_date UNIQUE (symbol, mic_code, price_date)
);
