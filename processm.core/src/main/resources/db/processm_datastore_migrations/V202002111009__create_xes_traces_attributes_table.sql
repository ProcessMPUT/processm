CREATE TABLE traces_attributes
(
    id           BIGSERIAL PRIMARY KEY,
    trace_id     BIGINT NOT NULL,
    parent_id    BIGINT,
    type         TEXT,
    key          TEXT,
    string_value TEXT,
    date_value   timestamptz,
    int_value    INT,
    bool_value   BOOLEAN,
    real_value   DOUBLE PRECISION,
    in_list_attr BOOLEAN
);

SELECT create_hypertable('traces_attributes', 'id', chunk_time_interval => 100000, if_not_exists => TRUE);

CREATE INDEX traces_attributes_log_id_index ON traces_attributes (trace_id);
CREATE INDEX traces_attributes_parent_id_index ON traces_attributes (parent_id);
