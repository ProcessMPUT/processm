CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE etl_configurations
(
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name                   TEXT NOT NULL,
    jdbc_uri               TEXT NOT NULL,
    "user"                 TEXT,
    password               TEXT,
    query                  TEXT NOT NULL,
    refresh                BIGINT CHECK (refresh >= 0),
    enabled                BOOLEAN          DEFAULT true,
    log_identity_id        UUID NOT NULL,
    last_event_external_id text
);

CREATE TABLE etl_column_to_attribute_maps
(
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    configuration_id UUID    NOT NULL REFERENCES etl_configurations (id),
    source           TEXT    NOT NULL,
    target           TEXT    NOT NULL,
    trace_id         BOOLEAN NOT NULL DEFAULT false,
    event_id         BOOLEAN NOT NULL DEFAULT false
);

CREATE INDEX etl_column_to_attribute_maps_configuration ON etl_column_to_attribute_maps (configuration_id);
