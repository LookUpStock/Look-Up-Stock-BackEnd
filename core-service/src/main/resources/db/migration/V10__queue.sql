-- BE #114: Queue-Tabelle für die asynchrone Kommunikation (ADR-07, Variante B2)
-- Jede Zeile ist eine Nachricht. Verbraucher holen sie mit FOR UPDATE SKIP LOCKED ab
-- und löschen sie nach erfolgreicher Verarbeitung.

CREATE TABLE queue_message (
    id         BIGSERIAL    PRIMARY KEY,
    -- Thema der Nachricht, z. B. nutzung.ereignis
    topic      VARCHAR(50)  NOT NULL,
    -- Inhalt als JSON, Aufbau je Thema
    payload    JSONB        NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT now(),
    -- Anzahl fehlgeschlagener Verarbeitungsversuche
    attempts   INTEGER      NOT NULL DEFAULT 0,
    -- Nach drei Fehlversuchen true; die Nachricht wird dann nicht mehr abgeholt
    failed     BOOLEAN      NOT NULL DEFAULT false,
    last_error VARCHAR(500)
);

-- Verbraucher fragen je Thema die ältesten offenen Nachrichten ab
CREATE INDEX idx_queue_message_open ON queue_message (topic, id) WHERE failed = false;