-- Bisherige Portfolios sind Testdaten ohne Besitzer und werden entfernt,


DELETE FROM position;
DELETE FROM portfolio;

ALTER TABLE portfolio
    ADD COLUMN user_id BIGINT NOT NULL REFERENCES app_user (id) ON DELETE CASCADE;

CREATE INDEX idx_portfolio_user ON portfolio (user_id);-- BE #54: Jedes Portfolio gehört einem Nutzer (US-01, NF-04)
