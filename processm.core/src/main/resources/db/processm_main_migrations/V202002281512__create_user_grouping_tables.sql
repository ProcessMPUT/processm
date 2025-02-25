CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE group_roles (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    role text NOT NULL
);

CREATE TABLE organization_roles (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name text NOT NULL
);

CREATE TABLE organizations (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name text NOT NULL,
    parent_organization_id uuid,
    is_private boolean NOT NULL,
    shared_group_id uuid NOT NULL
);

CREATE TABLE user_groups (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    parent_group_id uuid,
    group_role_id uuid NOT NULL,
    name text,
    is_implicit boolean NOT NULL DEFAULT FALSE
);

CREATE TABLE user_groups_with_workspaces (
    user_group_id uuid NOT NULL,
    workspace_id uuid NOT NULL,
    organization_id uuid NOT NULL
);

CREATE TABLE users (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    username text NOT NULL,
    private_group_id uuid NOT NULL
);

CREATE TABLE users_in_groups (
    user_id uuid NOT NULL,
    user_group_id uuid NOT NULL,
    creation_date timestamptz
);

CREATE TABLE users_roles_in_organizations (
    user_id uuid NOT NULL,
    organization_role_id uuid NOT NULL,
    organization_id uuid NOT NULL,
    creation_date timestamptz
);

CREATE TABLE workspaces (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name text NOT NULL
);

CREATE TABLE workspace_components (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name text NOT NULL,
    workspace_id uuid NOT NULL,
    query text NOT NULL,
    type text NOT NULL,
    data_source_id integer NOT NULL,
    customization_data text NULL
);

ALTER TABLE user_groups_with_workspaces
    ADD CONSTRAINT user_groups_with_workspaces_pk PRIMARY KEY (user_group_id, workspace_id, organization_id);
ALTER TABLE users_in_groups
    ADD CONSTRAINT users_in_groups_pk PRIMARY KEY (user_id, user_group_id);
ALTER TABLE users_roles_in_organizations
    ADD CONSTRAINT users_roles_in_organizations_pk PRIMARY KEY (user_id, organization_role_id, organization_id);
ALTER TABLE organizations
    ADD CONSTRAINT organizations_organizations_fk FOREIGN KEY (parent_organization_id) REFERENCES organizations(id);
ALTER TABLE organizations
    ADD CONSTRAINT organizations_user_groups_fk FOREIGN KEY (shared_group_id) REFERENCES user_groups(id) ON DELETE CASCADE;
ALTER TABLE user_groups
    ADD CONSTRAINT user_groups_user_groups_fk FOREIGN KEY (parent_group_id) REFERENCES user_groups(id);
ALTER TABLE user_groups
    ADD CONSTRAINT user_groups_group_roles_fk FOREIGN KEY (group_role_id) REFERENCES group_roles(id);
ALTER TABLE user_groups_with_workspaces
    ADD CONSTRAINT user_groups_with_workspaces_user_groups_fk FOREIGN KEY (user_group_id) REFERENCES user_groups(id) ON DELETE CASCADE;
ALTER TABLE user_groups_with_workspaces
    ADD CONSTRAINT user_groups_with_workspaces_workspaces_fk FOREIGN KEY (workspace_id) REFERENCES workspaces(id) ON DELETE CASCADE;
ALTER TABLE users_in_groups
    ADD CONSTRAINT users_in_groups_user_groups_fk FOREIGN KEY (user_group_id) REFERENCES user_groups(id) ON DELETE CASCADE;
ALTER TABLE users_in_groups
    ADD CONSTRAINT users_in_groups_users_fk FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;
ALTER TABLE user_groups_with_workspaces
    ADD CONSTRAINT user_groups_with_workspaces_organizations_fk FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE CASCADE;
ALTER TABLE users
    ADD CONSTRAINT users_user_groups_fk FOREIGN KEY (private_group_id) REFERENCES user_groups(id) ON DELETE CASCADE;
ALTER TABLE users_roles_in_organizations
    ADD CONSTRAINT users_roles_in_organizations_organization_roles_fk FOREIGN KEY (organization_role_id) REFERENCES organization_roles(id);
ALTER TABLE users_roles_in_organizations
    ADD CONSTRAINT users_roles_in_organizations_organizations_fk FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE CASCADE;
ALTER TABLE users_roles_in_organizations
    ADD CONSTRAINT users_roles_in_organizations_users_fk FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;
ALTER TABLE workspace_components
    ADD CONSTRAINT workspace_components_workspaces_fk FOREIGN KEY (workspace_id) REFERENCES workspaces(id) ON DELETE CASCADE;
