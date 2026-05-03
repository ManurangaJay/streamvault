-- V2__Create_transaction_projections_table.sql

CREATE TABLE transaction_projections (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    amount NUMERIC(18,2) NOT NULL,
    direction VARCHAR(6) NOT NULL,
    balance_after NUMERIC(18,2) NOT NULL,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    correlation_id UUID NOT NULL
);

CREATE INDEX idx_transaction_projections_account_id ON transaction_projections(account_id);

CREATE INDEX idx_transaction_projections_created_at ON transaction_projections(created_at);