CREATE TABLE data_sources (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name text NOT NULL,
    organization_id uuid NOT NULL,
    creation_date timestamptz
);

ALTER TABLE data_sources
    ADD CONSTRAINT data_sources_organizations_fk FOREIGN KEY (organization_id) REFERENCES organizations(id);