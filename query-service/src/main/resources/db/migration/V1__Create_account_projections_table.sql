CREATE TABLE account_projections (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    balance NUMERIC(18,2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    account_type VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL,
    transaction_count BIGINT NOT NULL DEFAULT 0,
    last_updated_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_account_projections_owner_id ON account_projections(owner_id);