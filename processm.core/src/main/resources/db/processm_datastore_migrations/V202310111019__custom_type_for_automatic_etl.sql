-- The types should be consistent with the respective columns of the object_versions table

CREATE TYPE remote_object_identifier AS (class_id INTEGER, object_id TEXT);