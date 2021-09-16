ALTER TABLE etl_column_to_attribute_maps
    DROP CONSTRAINT etl_column_to_attribute_maps_configuration_id_fkey;

ALTER TABLE etl_column_to_attribute_maps
    ADD CONSTRAINT etl_column_to_attribute_maps_configuration_id_fkey
        FOREIGN KEY (configuration_id)
            REFERENCES etl_configurations (id)
            ON DELETE CASCADE
            ON UPDATE CASCADE;


ALTER TABLE etl_configurations
    ADD COLUMN last_execution_time timestamptz NULL;

CREATE TABLE etl_errors
(
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    configuration_id UUID NOT NULL REFERENCES etl_configurations (id) ON DELETE CASCADE ON UPDATE CASCADE,
    message          TEXT NOT NULL,
    exception        TEXT,
    time             timestamptz      DEFAULT current_timestamp
)
