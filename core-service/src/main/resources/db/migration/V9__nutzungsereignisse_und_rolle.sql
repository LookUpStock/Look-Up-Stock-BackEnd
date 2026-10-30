-- BE #110: Nutzungsereignisse (US-11, E36) und Rolle im Nutzerkonto (E40)

-- Rolle: USER oder MANAGER (Geschäftsführung), Vergabe nur per Datenbank
ALTER TABLE app_user
    ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'USER',
    ADD CONSTRAINT chk_app_user_role CHECK (role IN ('USER', 'MANAGER'));

-- Anonym: nur Ereignisart und Zeitpunkt, kein Fremdschlüssel auf app_user (E36)
CREATE TABLE usage_event (
    id          BIGSERIAL   PRIMARY KEY,
    event_type  VARCHAR(30) NOT NULL,
    occurred_at TIMESTAMP   NOT NULL
);

CREATE INDEX idx_usage_event_occurred_at ON usage_event (occurred_at);