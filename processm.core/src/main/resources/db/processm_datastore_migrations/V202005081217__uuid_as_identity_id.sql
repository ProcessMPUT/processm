ALTER TABLE logs
    ALTER COLUMN "identity:id" TYPE uuid USING "identity:id"::uuid;
ALTER TABLE traces
    ALTER COLUMN "identity:id" TYPE uuid USING "identity:id"::uuid;
ALTER TABLE events
    ALTER COLUMN "identity:id" TYPE uuid USING "identity:id"::uuid;

ALTER TABLE globals
    ADD COLUMN uuid_value uuid;
ALTER TABLE logs_attributes
    ADD COLUMN uuid_value uuid;
ALTER TABLE traces_attributes
    ADD COLUMN uuid_value uuid;
ALTER TABLE events_attributes
    ADD COLUMN uuid_value uuid;