ALTER TABLE users
    DROP column username;

ALTER TABLE organization_roles
    RENAME TO roles;

INSERT INTO roles(name)
VALUES ('none');

ALTER TABLE organizations
    DROP COLUMN shared_group_id;

ALTER TABLE user_groups
    RENAME TO groups;

ALTER TABLE groups
    DROP COLUMN group_role_id;

ALTER TABLE groups
    ADD COLUMN organization_id uuid null references organizations (id);

ALTER TABLE groups
    ADD COLUMN is_shared boolean not null default false;

ALTER TABLE groups
    ADD CONSTRAINT organization_id_is_implicit CHECK (is_implicit = (organization_id IS NULL));

ALTER TABLE groups
    ADD CONSTRAINT organization_id_is_shared CHECK (NOT is_shared OR (organization_id IS NOT NULL));

ALTER TABLE users_in_groups
    RENAME COLUMN user_group_id to group_id;

DROP TABLE user_groups_with_workspaces;
DROP TABLE group_roles;

ALTER TABLE users_roles_in_organizations
    DROP CONSTRAINT users_roles_in_organizations_pk;

ALTER TABLE users_roles_in_organizations
    ADD COLUMN id uuid PRIMARY KEY DEFAULT gen_random_uuid();

CREATE TABLE access_control_list
(
    id       uuid NOT NULL PRIMARY KEY DEFAULT gen_random_uuid(),
    urn      text NOT NULL,
    group_id uuid NOT NULL REFERENCES groups (id),
    role_id  uuid NOT NULL REFERENCES roles (id)
);

CREATE UNIQUE INDEX access_control_list_urn_group_id ON access_control_list (urn, group_id);
CREATE UNIQUE INDEX groups_organization_id_is_shared ON groups (organization_id) WHERE is_shared = true;

