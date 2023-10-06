import BaseService from "./BaseService";
import { Group } from "@/openapi";

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
}
