-- erstellen der nötigen Tabellen

CREATE TABLE asset (
    id        BIGSERIAL    PRIMARY KEY,
    symbol    VARCHAR(20)  NOT NULL,
    isin      VARCHAR(12),
    name      VARCHAR(255) NOT NULL,
    exchange  VARCHAR(50),
    currency  VARCHAR(3),
    type      VARCHAR(20)
);
 
CREATE TABLE portfolio (
    id          BIGSERIAL    PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT now()
);
 
CREATE TABLE position (
    id            BIGSERIAL     PRIMARY KEY,
    portfolio_id  BIGINT        NOT NULL REFERENCES portfolio (id) ON DELETE CASCADE,
    asset_id      BIGINT        NOT NULL REFERENCES asset (id),
    weight        NUMERIC(5, 2) NOT NULL CHECK (weight > 0),
    CONSTRAINT uq_position_portfolio_asset UNIQUE (portfolio_id, asset_id)
);
 