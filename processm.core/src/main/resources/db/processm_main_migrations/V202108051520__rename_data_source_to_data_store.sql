ALTER TABLE workspace_components
    RENAME COLUMN data_source_id TO data_store_id;

ALTER TABLE data_sources
    RENAME TO data_stores;

ALTER TABLE data_stores RENAME CONSTRAINT data_sources_organizations_fk TO data_stores_organizations_fk;