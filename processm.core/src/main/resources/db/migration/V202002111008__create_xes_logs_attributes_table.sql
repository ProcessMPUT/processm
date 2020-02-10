CREATE TABLE logs_attributes
(
    id           BIGSERIAL PRIMARY KEY,
    log_id       INTEGER NOT NULL,
    parent_id    BIGINT,
    type         TEXT,
    key          TEXT,
    string_value TEXT,
    date_value   timestamptz,
    int_value    INT,
    bool_value   BOOLEAN,
    real_value   REAL
);

SELECT create_hypertable('logs_attributes', 'id', chunk_time_interval => 100000, if_not_exists => TRUE);

CREATE INDEX logs_attributes_log_id_index ON logs_attributes (log_id);
CREATE INDEX logs_attributes_parent_id_index ON logs_attributes (parent_id);
