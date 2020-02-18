CREATE EXTENSION IF NOT EXISTS timescaledb SCHEMA current_schema CASCADE;

CREATE TABLE durable_storage(
    urn         VARCHAR(1024) NOT NULL PRIMARY KEY,
    data        JSON NOT NULL -- use JSON instead of JSONB, because we do not search these objects in DB
    -- and this way storing and restoring them is faster. The serializer also should not struggle with
    -- deserializing the objects that it in fact serialized.
)

