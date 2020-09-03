CREATE EXTENSION IF NOT EXISTS timescaledb SCHEMA ${flyway:defaultSchema} CASCADE;
CREATE EXTENSION IF NOT EXISTS dblink;

CREATE TABLE durable_storage(
    urn         VARCHAR(1024) NOT NULL PRIMARY KEY,
    data        JSON NOT NULL -- use JSON instead of JSONB, because we do not search these objects in DB
    -- and this way storing and restoring them is faster. The serializer also should not struggle with
    -- deserializing the objects that it in fact serialized.
);

CREATE OR REPLACE FUNCTION create_database(database text)
    RETURNS BOOLEAN
    LANGUAGE plpgsql
    STRICT
AS
$$
BEGIN
    IF EXISTS (SELECT FROM pg_database WHERE datname = database) THEN
        RAISE NOTICE 'Database already exists';  -- optional
    ELSE
        PERFORM dblink_exec('dbname=' || current_database(), concat('CREATE DATABASE "', database, '"'));
    END IF;

    RETURN EXISTS(SELECT 1 FROM pg_database WHERE datname = database);
END
$$;

