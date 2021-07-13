ALTER TABLE workspace_components
    ADD COLUMN layout_data text NULL;

ALTER TABLE workspace_components
    ALTER COLUMN data_source_id DROP NOT NULL;

ALTER TABLE public.data_sources
    DROP CONSTRAINT data_sources_organizations_fk,
    ADD CONSTRAINT data_sources_organizations_fk
    FOREIGN KEY (organization_id)
    REFERENCES organizations(id)
    ON DELETE CASCADE;