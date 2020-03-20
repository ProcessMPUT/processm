CREATE TABLE group_roles (
    id bigserial PRIMARY KEY,
    role text NOT NULL
);

CREATE TABLE organization_roles (
    id bigserial PRIMARY KEY,
    name text NOT NULL
);

CREATE TABLE organizations (
    id bigserial PRIMARY KEY,
    name text NOT NULL,
    parent_organization_id bigint,
    is_private boolean NOT NULL
);

CREATE TABLE user_groups (
    id bigserial PRIMARY KEY,
    parent_group_id bigint NOT NULL,
    group_role_id bigint NOT NULL,
    name text
);

CREATE TABLE user_groups_with_workspaces (
    user_group_id bigint NOT NULL,
    workspace_id bigint NOT NULL
);

CREATE TABLE users (
    id bigserial PRIMARY KEY,
    username text NOT NULL
);

CREATE TABLE users_in_groups (
    user_id bigint NOT NULL,
    user_group_id bigint NOT NULL,
    organization_id bigint NOT NULL,
    creation_date timestamptz
);

CREATE TABLE users_roles_in_organizations (
    user_id bigint NOT NULL,
    organization_role_id bigint NOT NULL,
    organization_id bigint NOT NULL,
    creation_date timestamptz
);

CREATE TABLE workspaces (
    id bigserial PRIMARY KEY
);

ALTER TABLE user_groups_with_workspaces
    ADD CONSTRAINT user_groups_with_workspaces_pk PRIMARY KEY (user_group_id, workspace_id);
ALTER TABLE users_in_groups
    ADD CONSTRAINT users_in_groups_pk PRIMARY KEY (user_id, user_group_id);
ALTER TABLE users_roles_in_organizations
    ADD CONSTRAINT users_roles_in_organizations_pk PRIMARY KEY (user_id, organization_role_id, organization_id);

ALTER TABLE organizations
    ADD CONSTRAINT organizations_organizations_fk FOREIGN KEY (parent_organization_id) REFERENCES organizations(id);
ALTER TABLE user_groups
    ADD CONSTRAINT user_groups_user_groups_fk FOREIGN KEY (parent_group_id) REFERENCES user_groups(id);
ALTER TABLE user_groups
    ADD CONSTRAINT user_groups_group_roles_fk FOREIGN KEY (group_role_id) REFERENCES group_roles(id);
ALTER TABLE user_groups_with_workspaces
    ADD CONSTRAINT user_groups_with_workspaces_user_groups_fk FOREIGN KEY (user_group_id) REFERENCES user_groups(id) ON DELETE CASCADE;
ALTER TABLE user_groups_with_workspaces
    ADD CONSTRAINT user_groups_with_workspaces_workspaces_fk FOREIGN KEY (workspace_id) REFERENCES workspaces(id) ON DELETE CASCADE;
ALTER TABLE users_in_groups
    ADD CONSTRAINT users_in_groups_organizations_fk FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE CASCADE;
ALTER TABLE users_in_groups
    ADD CONSTRAINT users_in_groups_user_groups_fk FOREIGN KEY (user_group_id) REFERENCES user_groups(id) ON DELETE CASCADE;
ALTER TABLE users_in_groups
    ADD CONSTRAINT users_in_groups_users_fk FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;
ALTER TABLE users_roles_in_organizations
    ADD CONSTRAINT users_roles_in_organizations_organization_roles_fk FOREIGN KEY (organization_role_id) REFERENCES organization_roles(id);
ALTER TABLE users_roles_in_organizations
    ADD CONSTRAINT users_roles_in_organizations_organizations_fk FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE CASCADE;
ALTER TABLE users_roles_in_organizations
    ADD CONSTRAINT users_roles_in_organizations_users_fk FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;
