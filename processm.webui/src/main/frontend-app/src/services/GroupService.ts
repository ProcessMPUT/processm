import BaseService from "./BaseService";
import { EntityID, Group, UserInfo } from "@/openapi";

export default class GroupService extends BaseService {
  public async getUserGroups(organizationId: string): Promise<Array<Group>> {
    const response = await this.groupsApi.getGroups(organizationId);
    return response.data;
  }

  public async createGroup(organizationId: string, name: string): Promise<Group> {
    const group = {
      name: name,
      organizationId: organizationId
    };
    const response = await this.groupsApi.createGroup(organizationId, group);
    return response.data;
  }

  public async updateGroup(organizationId: string, groupId: string, name: string) {
    const group = {
      id: groupId,
      name: name,
      isImplicit: false,
      isShared: false,
      organizationId: organizationId
    };
    const response = await this.groupsApi.updateGroup(organizationId, groupId, group);
    return response.data;
  }

  public async removeGroup(organizationId: string, groupId: string): Promise<void> {
    const response = await this.groupsApi.removeGroup(organizationId, groupId);
    return response.data;
  }

  public async addMember(organizationId: string, groupId: string, userId: string): Promise<boolean> {
    const response = await this.groupsApi.addGroupMember(organizationId, groupId, userId);
    return response.status == 201;
  }

  public async getMembers(organizationId: string, groupId: string): Promise<UserInfo[]> {
    const response = await this.groupsApi.getGroupMembers(organizationId, groupId);
    return response.data;
  }

  public async removeMember(organizationId: string, groupId: string, userId: string): Promise<boolean> {
    const response = await this.groupsApi.removeGroupMember(organizationId, groupId, userId);
    return response.status == 204;
  }

  public async getSoleOwnershipObjects(organizationId: string, groupId: string): Promise<EntityID[]> {
    const response = await this.groupsApi.getGroupSoleOwnership(organizationId, groupId);
    return response.data;
  }
}
