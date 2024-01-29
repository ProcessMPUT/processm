import BaseService from "./BaseService";
import {Organization, OrganizationMember, OrganizationRole} from "@/openapi/api";

export default class OrganizationService extends BaseService {
    public async getOrganizationMembers(organizationId: string): Promise<OrganizationMember[]> {
        const response = await this.organizationsApi.getOrganizationMembers(organizationId);
        return response.data;
    }

    public async addMember(organizationId: string, email: string, role: OrganizationRole): Promise<void> {
        const member = {email: email, organizationRole: role};
        await this.organizationsApi.addOrganizationMember(organizationId, member);
    }

    public async updateRole(organizationId: string, userId: string, role: OrganizationRole): Promise<void> {
        const member = {organizationRole: role};
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
        return response.status == 201;
    }

    public async detach(organizationId: string, subOrganizationId: string) {
        const response = await this.organizationsApi.detachSubOrganization(organizationId, subOrganizationId);
        return response.status == 201;
    }

    public async removeOrganization(organizationId: string) {
        const response = await this.organizationsApi.removeOrganization(organizationId);
        return response.status == 201;
    }
}
