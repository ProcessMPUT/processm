ALTER TABLE etl_configurations
    ADD CONSTRAINT etl_configurations_etl_processes_metadata_fk FOREIGN KEY (id) REFERENCES etl_processes_metadata(id) ON DELETE CASCADE;