CREATE TABLE users (
    id UUID PRIMARY KEY NOT NULL,
    full_name VARCHAR(200) NOT NULL,
    email VARCHAR(320) NOT NULL UNIQUE,
    password_hash VARCHAR(256) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE accounts (
    id UUID PRIMARY KEY NOT NULL,
    owner_id UUID NOT NULL,
    account_type VARCHAR(50) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_accounts_owner FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE RESTRICT
);

CREATE INDEX idx_accounts_owner_id ON accounts (owner_id);