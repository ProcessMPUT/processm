CREATE TABLE globals
(
    id           SERIAL PRIMARY KEY,
    log_id       INTEGER NOT NULL,
    parent_id    INTEGER,
    scope        TEXT,
    type         TEXT,
    key          TEXT,
    string_value TEXT,
    date_value   timestamptz,
    int_value    INT,
    bool_value   BOOLEAN,
    real_value   DOUBLE PRECISION,
    in_list_attr BOOLEAN
);

SELECT create_hypertable('globals', 'id', chunk_time_interval => 100000, if_not_exists => TRUE);

CREATE INDEX globals_log_id_index ON globals (log_id);
CREATE INDEX globals_parent_id_index ON globals (parent_id);
