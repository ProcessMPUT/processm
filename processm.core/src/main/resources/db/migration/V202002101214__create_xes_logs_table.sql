CREATE TABLE logs
(
    id                SERIAL PRIMARY KEY,
    "concept:name"    TEXT,
    "identity:id"     TEXT,
    "lifecycle:model" TEXT
);

SELECT create_hypertable('logs', 'id', chunk_time_interval => 100000, if_not_exists => TRUE);