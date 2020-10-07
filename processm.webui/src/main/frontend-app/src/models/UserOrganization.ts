import { OrganizationRole } from "./OrganizationRole";

export default class UserOrganization {
  constructor(
    public id: string,
    public name: string,
    public organizationRole: OrganizationRole
  ) {}
}
