import BaseService from "./BaseService";
import OrganizationMember from "@/models/OrganizationMember";

export default class OrganizationService extends BaseService {
  public async getOrganizationMembers(organizationId: string): Promise<OrganizationMember[]> {
    const response = await this.organizationsApi.getOrganizationMembers(organizationId);
    if (response.status != 200) {
      throw new Error(response.statusText);
    }
    return response.data;
  }
}
