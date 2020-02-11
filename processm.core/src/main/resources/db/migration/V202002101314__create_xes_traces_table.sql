CREATE TABLE traces
(
    id              SERIAL PRIMARY KEY,
    log_id          INTEGER NOT NULL,
    "concept:name"  TEXT,
    "cost:total"    REAL,
    "cost:currency" TEXT,
    "identity:id"   TEXT
);

SELECT create_hypertable('traces', 'id', chunk_time_interval => 100000, if_not_exists => TRUE);

CREATE INDEX traces_log_id_index ON traces (log_id);
