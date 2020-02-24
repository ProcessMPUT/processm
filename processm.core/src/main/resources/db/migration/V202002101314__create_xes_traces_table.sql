CREATE TABLE traces
(
    id              BIGSERIAL PRIMARY KEY,
    log_id          INTEGER NOT NULL,
    "concept:name"  TEXT,
    "cost:total"    DOUBLE PRECISION,
    "cost:currency" TEXT,
    "identity:id"   TEXT,
    event_stream    BOOLEAN
);

SELECT create_hypertable('traces', 'id', chunk_time_interval => 100000, if_not_exists => TRUE);

CREATE INDEX traces_log_id_index ON traces (log_id);
