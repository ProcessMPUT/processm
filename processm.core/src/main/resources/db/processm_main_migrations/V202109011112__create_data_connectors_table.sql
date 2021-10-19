CREATE TABLE data_connectors (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name text NOT NULL,
    last_connection_status boolean,
    connection_properties text NOT NULL,
    data_store_id uuid NOT NULL
);

ALTER TABLE data_connectors
    ADD CONSTRAINT data_connectors_data_stores_fk FOREIGN KEY (data_store_id) REFERENCES data_stores(id);