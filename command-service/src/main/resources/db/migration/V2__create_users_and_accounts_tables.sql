CREATE TABLE users (
    id UUID PRIMARY KEY NOT NULL, [cite: 100]
    full_name VARCHAR(200) NOT NULL, [cite: 100]
    email VARCHAR(320) NOT NULL UNIQUE, [cite: 100]
    password_hash VARCHAR(256) NOT NULL, [cite: 100]
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW() [cite: 100]
);

CREATE TABLE accounts (
    id UUID PRIMARY KEY NOT NULL, [cite: 98]
    owner_id UUID NOT NULL, [cite: 98]
    account_type VARCHAR(50) NOT NULL, [cite: 98]
    currency VARCHAR(3) NOT NULL, [cite: 98]
    status VARCHAR(20) NOT NULL, [cite: 98]
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), [cite: 98]
    CONSTRAINT fk_accounts_owner FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE RESTRICT
);

CREATE INDEX idx_accounts_owner_id ON accounts (owner_id);