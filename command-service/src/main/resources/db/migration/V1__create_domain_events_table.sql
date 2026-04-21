CREATE TABLE domain_events (
    id BIGSERIAL PRIMARY KEY NOT NULL,
    aggregate_id UUID NOT NULL,
    aggregate_type VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    event_data JSONB NOT NULL,
    event_version BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    correlation_id UUID NOT NULL,
    CONSTRAINT enq_aggregate_version UNIQUE (aggregate_id, event_version)
);

CREATE INDEX idx_domain_events_aggregate_id ON domain_events (aggregate_id);