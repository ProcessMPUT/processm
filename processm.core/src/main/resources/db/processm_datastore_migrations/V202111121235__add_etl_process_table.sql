CREATE TABLE etl_processes_metadata (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name text NOT NULL,
    process_type text NOT NULL,
    creation_date timestamptz NOT NULL DEFAULT NOW(),
    last_updated_date timestamptz,
    data_connector_id uuid NOT NULL
);

ALTER TABLE etl_processes_metadata
    ADD CONSTRAINT etl_processes_metadata_data_connectors_fk FOREIGN KEY (data_connector_id) REFERENCES data_connectors(id) ON DELETE CASCADE;

CREATE TABLE automatic_etl_processes (
    id uuid PRIMARY KEY
);

ALTER TABLE automatic_etl_processes
    ADD CONSTRAINT automatic_etl_processes_etl_processes_metadata_fk FOREIGN KEY (id) REFERENCES etl_processes_metadata(id) ON DELETE CASCADE;

CREATE TABLE automatic_etl_processes_relations (
    id uuid PRIMARY KEY,
    automatic_etl_process_id uuid NOT NULL,
    source_class_id text NOT NULL,
    target_class_id text NOT NULL
);

ALTER TABLE automatic_etl_processes_relations
    ADD CONSTRAINT automatic_etl_processes_relations_automatic_etl_processes_fk FOREIGN KEY (automatic_etl_process_id) REFERENCES automatic_etl_processes(id) ON DELETE CASCADE;