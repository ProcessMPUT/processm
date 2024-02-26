import BaseService from "./BaseService";
import { Organization, OrganizationMember, OrganizationRole } from "@/openapi/api";
import Vue from "vue";

export default class OrganizationService extends BaseService {
  public async getOrganizationMembers(organizationId: string): Promise<OrganizationMember[]> {
    const response = await this.organizationsApi.getOrganizationMembers(organizationId);
    return response.data;
  }

  public async addMember(organizationId: string, email: string, role: OrganizationRole): Promise<void> {
    const member = { email: email, organizationRole: role };
    await this.organizationsApi.addOrganizationMember(organizationId, member);
  }

  public async updateRole(organizationId: string, userId: string, role: OrganizationRole): Promise<void> {
    const member = { organizationRole: role };
    await this.organizationsApi.updateOrganizationMember(organizationId, userId, member);
  }

  public async removeMember(organizationId: string, userId: string): Promise<void> {
    await this.organizationsApi.removeOrganizationMember(organizationId, userId);
  }

  public async getOrganizations(): Promise<Array<Organization>> {
    const response = await this.organizationsApi.getOrganizations();
    return response.data;
  }

  public async attach(organizationId: string, subOrganizationId: string) {
    const response = await this.organizationsApi.attachSubOrganization(organizationId, subOrganizationId);
    return response.status == 204;
  }

  public async detach(organizationId: string, subOrganizationId: string) {
    const response = await this.organizationsApi.detachSubOrganization(organizationId, subOrganizationId);
    return response.status == 204;
  }

  public async removeOrganization(organizationId: string) {
    const response = await this.organizationsApi.removeOrganization(organizationId);
    if (response.status == 204) {
      await this.updateClaims();
      return true;
    }
    return false;
  }

  public async getSoleOwnership(organizationId: string) {
    const response = await this.organizationsApi.getOrganizationSoleOwnership(organizationId);
    return response.data;
  }

  protected async updateClaims() {
    // refresh token to get new permissions
    await this.prolongExistingSession(undefined);

    const response = await this.usersApi.getUserOrganizations();

    if (response.status == 200) {
      Vue.prototype.$sessionStorage.userOrganizations = response.data;
    }
  }

  public async createOrganization(name: string, isPrivate: boolean) {
    const response = await this.organizationsApi.createOrganization({ name: name, isPrivate: isPrivate });
    if (response.status == 201) {
      await this.updateClaims();
      return response.data;
    } else return undefined;
  }

  public async updateOrganization(organizationId: string, name: string, isPrivate: boolean) {
    await this.organizationsApi.updateOrganization(organizationId, { name: name, isPrivate: isPrivate });
  }
}
