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
    real_value   DOUBLE PRECISION,
    in_list_attr BOOLEAN
);

SELECT create_hypertable('events_attributes', by_range('id', 262144), if_not_exists => TRUE);

CREATE INDEX events_attributes_log_id_index ON events_attributes (event_id);
CREATE INDEX events_attributes_parent_id_index ON events_attributes (parent_id);
