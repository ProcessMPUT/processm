-- Drop old indexes.
DROP INDEX IF EXISTS logs_attributes_key;
DROP INDEX IF EXISTS traces_attributes_key;
DROP INDEX IF EXISTS events_attributes_key;

-- Create hash-based indexes.
CREATE INDEX logs_attributes_key ON logs_attributes USING HASH ("key") WHERE "key" IS NOT NULL;
CREATE INDEX traces_attributes_key ON traces_attributes USING HASH ("key") WHERE "key" IS NOT NULL;
CREATE INDEX events_attributes_key ON events_attributes USING HASH ("key") WHERE "key" IS NOT NULL;
