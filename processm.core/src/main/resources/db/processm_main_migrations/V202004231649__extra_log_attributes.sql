ALTER TABLE logs
    RENAME COLUMN features TO "xes:features";

ALTER TABLE logs
    ADD COLUMN "xes:version" TEXT;