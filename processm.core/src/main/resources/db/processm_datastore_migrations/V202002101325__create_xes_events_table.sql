CREATE TABLE events
(
    id                     BIGSERIAL PRIMARY KEY,
    trace_id               BIGSERIAL NOT NULL,
    "concept:name"         TEXT,
    "concept:instance"     TEXT,
    "cost:total"           DOUBLE PRECISION,
    "cost:currency"        TEXT,
    "identity:id"          TEXT,
    "lifecycle:transition" TEXT,
    "lifecycle:state"      TEXT,
    "org:resource"         TEXT,
    "org:role"             TEXT,
    "org:group"            TEXT,
    "time:timestamp"       timestamptz
);

SELECT create_hypertable('events', by_range('id', 65536), if_not_exists => TRUE);

CREATE INDEX events_log_id_index ON events (trace_id);
