CREATE TABLE events_attributes
(
    id           BIGSERIAL PRIMARY KEY,
    event_id     BIGINT NOT NULL,
    parent_id    BIGINT,
    type         TEXT,
    key          TEXT,
    string_value TEXT,
    date_value   timestamptz,
    int_value    INT,
    bool_value   BOOLEAN,
    real_value   REAL
);

SELECT create_hypertable('events_attributes', 'id', chunk_time_interval => 100000, if_not_exists => TRUE);

CREATE INDEX events_attributes_log_id_index ON events_attributes (event_id);
CREATE INDEX events_attributes_parent_id_index ON events_attributes (parent_id);
