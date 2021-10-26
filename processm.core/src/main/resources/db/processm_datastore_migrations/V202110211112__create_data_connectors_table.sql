CREATE TABLE data_connectors (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name text NOT NULL,
    last_connection_status boolean,
    last_connection_status_timestamp timestamptz,
    connection_properties text NOT NULL
);