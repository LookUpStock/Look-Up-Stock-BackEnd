-- BE #30: Nutzer und Anmelde-Tokens (US-01)
-- "user" ist in PostgreSQL reserviert, daher app_user

CREATE TABLE app_user (
    id            BIGSERIAL    PRIMARY KEY,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT uq_app_user_email UNIQUE (email)
);

CREATE TABLE auth_token (
    id         BIGSERIAL   PRIMARY KEY,
    token      VARCHAR(64) NOT NULL,
    user_id    BIGINT      NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    created_at TIMESTAMP   NOT NULL DEFAULT now(),
    expires_at TIMESTAMP   NOT NULL,
    CONSTRAINT uq_auth_token_token UNIQUE (token)
);

CREATE INDEX idx_auth_token_user ON auth_token (user_id);