ALTER TABLE logs_attributes
    ALTER COLUMN int_value TYPE bigint;

ALTER TABLE globals
    ALTER COLUMN int_value TYPE bigint;

ALTER TABLE traces_attributes
    ALTER COLUMN int_value TYPE bigint;

ALTER TABLE events_attributes
    ALTER COLUMN int_value TYPE bigint;