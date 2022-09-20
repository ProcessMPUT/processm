import BaseService from "./BaseService";
import { OrganizationMember, OrganizationRole } from "@/openapi/api";

export default class OrganizationService extends BaseService {
  public async getOrganizationMembers(organizationId: string): Promise<OrganizationMember[]> {
    const response = await this.organizationsApi.getOrganizationMembers(organizationId);
    return response.data;
  }

  public async addMember(organizationId: string, email: string, role: OrganizationRole): Promise<void> {
    const member = { email: email, organizationRole: role };
    await this.organizationsApi.addOrganizationMember(organizationId, member);
  }
}
