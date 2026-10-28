-- BE #39 / BE #50: Monatliche Kursdaten je Anlage zwischenspeichern (NF-07)
-- Kursdaten, die jünger als 24 h sind, werden aus dieser Tabelle verwendet,
-- ältere werden bei Twelve Data neu abgerufen und ersetzt.
CREATE TABLE price_history (
    id          BIGSERIAL     PRIMARY KEY,
    asset_id    BIGINT        NOT NULL REFERENCES asset (id) ON DELETE CASCADE,
    price_date  DATE          NOT NULL,
    close_price NUMERIC(19, 6) NOT NULL,
    fetched_at  TIMESTAMP     NOT NULL,
    CONSTRAINT uq_price_history_asset_date UNIQUE (asset_id, price_date)
);

CREATE INDEX idx_price_history_asset ON price_history (asset_id);
