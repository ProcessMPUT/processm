CREATE TABLE extensions
(
    id     SERIAL PRIMARY KEY,
    log_id INTEGER NOT NULL,
    name   TEXT,
    prefix TEXT,
    uri    TEXT
);

SELECT create_hypertable('extensions', 'id', chunk_time_interval => 100000, if_not_exists => TRUE);

CREATE INDEX extensions_log_id_index ON extensions (log_id);
