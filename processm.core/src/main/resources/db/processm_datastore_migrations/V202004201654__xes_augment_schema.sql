ALTER TABLE logs_attributes
    ALTER COLUMN log_id SET NOT NULL,
    ALTER COLUMN type SET NOT NULL;

ALTER TABLE globals
    ALTER COLUMN id TYPE BIGINT,
    ALTER COLUMN parent_id TYPE BIGINT,
    ALTER COLUMN log_id SET NOT NULL,
    ALTER COLUMN type SET NOT NULL;

ALTER TABLE traces
    ALTER COLUMN log_id SET NOT NULL;

ALTER TABLE traces_attributes
    ALTER COLUMN trace_id SET NOT NULL,
    ALTER COLUMN type SET NOT NULL;

ALTER TABLE events
    ALTER COLUMN trace_id SET NOT NULL;

ALTER TABLE events_attributes
    ALTER COLUMN event_id SET NOT NULL,
    ALTER COLUMN type SET NOT NULL;
