CREATE TABLE classifiers
(
    id     SERIAL PRIMARY KEY,
    log_id INTEGER NOT NULL,
    scope  TEXT,
    name   TEXT,
    keys   TEXT
);

SELECT create_hypertable('classifiers', 'id', chunk_time_interval => 100000, if_not_exists => TRUE);

CREATE INDEX classifiers_log_id_index ON classifiers (log_id);
