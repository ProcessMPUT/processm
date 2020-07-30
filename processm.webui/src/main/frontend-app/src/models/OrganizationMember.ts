import { OrganizationRole } from "./OrganizationRole";

export default class OrganizationMember {
  constructor(
    public id: string,
    public username: string,
    public organizationRole: OrganizationRole
  ) {}
}
