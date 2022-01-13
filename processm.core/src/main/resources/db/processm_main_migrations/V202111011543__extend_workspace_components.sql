ALTER TABLE workspace_components
    ADD COLUMN data TEXT NULL;

UPDATE workspace_components
SET data=data_store_id::text;

ALTER TABLE workspace_components
    DROP COLUMN data_store_id;

ALTER TABLE workspace_components
    ADD COLUMN data_store_id UUID NOT NULL DEFAULT '00000000-00000000-00000000-00000000'::uuid;

ALTER TABLE workspace_components
    ADD COLUMN user_last_modified TIMESTAMP NOT NULL DEFAULT '1970-01-01T00:00:00Z'::timestamp;

ALTER TABLE workspace_components
    ADD COLUMN data_last_modified TIMESTAMP NULL;

ALTER TABLE workspace_components
    ADD COLUMN last_error TEXT NULL;
