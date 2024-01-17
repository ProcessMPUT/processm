CREATE TABLE postponed_events_queue
(
    id              SERIAL PRIMARY KEY,
    -- No foreign key since TimescaleDB doesn't support them
    log_identity_id uuid      NOT NULL,
    "timestamp"     TIMESTAMP NOT NULL,
    object_id       TEXT      NOT NULL,
    class_id        INT       NOT NULL,
    db_event        TEXT      NOT NULL,
    CONSTRAINT fk_postponed_events_queue_class_id__id FOREIGN KEY (class_id) REFERENCES classes (id)
)