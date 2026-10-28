-- BE #68: Gespeicherte Simulationen (US-08)
-- Parameter als eigene Spalten, das berechnete Ergebnis (estimates, timeline, metrics)
-- als JSON, damit es beim Abrufen ohne Neuberechnung unverändert zurückgegeben wird.

CREATE TABLE simulation (
    id                   BIGSERIAL      PRIMARY KEY,
    user_id              BIGINT         NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    -- Wird das Portfolio gelöscht, werden seine gespeicherten Simulationen mitgelöscht
    portfolio_id         BIGINT         NOT NULL REFERENCES portfolio (id) ON DELETE CASCADE,
    name                 VARCHAR(100)   NOT NULL,
    start_capital        NUMERIC(19, 2) NOT NULL,
    monthly_contribution NUMERIC(19, 2) NOT NULL,
    horizon_years        INTEGER        NOT NULL,
    paths                INTEGER        NOT NULL,
    seed                 BIGINT         NOT NULL,
    -- Zielbetrag ist optional (US-05)
    target_amount        NUMERIC(19, 2),
    result               JSONB          NOT NULL,
    runtime_ms           BIGINT         NOT NULL,
    created_at           TIMESTAMP      NOT NULL DEFAULT now()
);

-- Die Liste wird immer pro Nutzer abgefragt
CREATE INDEX idx_simulation_user ON simulation (user_id);