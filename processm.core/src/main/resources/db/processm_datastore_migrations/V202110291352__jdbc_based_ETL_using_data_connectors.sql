ALTER TABLE etl_configurations
    DROP COLUMN jdbc_uri,
    DROP COLUMN "user",
    DROP COLUMN password,
    ADD COLUMN data_connector uuid NOT NULL REFERENCES data_connectors(id);