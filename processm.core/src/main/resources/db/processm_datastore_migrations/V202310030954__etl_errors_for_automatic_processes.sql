-- This is ugly, but the code to alter the table would not be easy to write
DROP TABLE etl_errors;

CREATE TABLE etl_errors
(
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    metadata_id      UUID NOT NULL REFERENCES etl_processes_metadata (id) ON DELETE CASCADE ON UPDATE CASCADE,
    message          TEXT NOT NULL,
    exception        TEXT,
    time             timestamptz      DEFAULT current_timestamp
);