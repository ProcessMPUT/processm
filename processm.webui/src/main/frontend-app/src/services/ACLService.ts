import BaseService from "./BaseService";
import {AccessControlEntry, AclApi, OrganizationRole} from "@/openapi";

export default class ACLService extends BaseService {

  protected get aclAPI() {
    return this.getGenericClient(AclApi);
  }

  protected encode(str: string): string {
    return Buffer.from(str, 'binary').toString('base64');
  }


  public async getACLFor(urn: string): Promise<Array<AccessControlEntry>> {

    const response = await this.aclAPI.aclUrnGet(this.encode(urn));

    console.assert(response.status == 200, response.statusText);
    return response.data;
  }

  public async createNewACE(urn: string, groupId: string, role: OrganizationRole) {
    await this.aclAPI.aclUrnPost(this.encode(urn), {groupId: groupId, role: role}, {
      validateStatus: (status: number) => status == 204
    });
  }

  public async updateACE(urn: string, groupId: string, role: OrganizationRole) {
    const response = await this.aclAPI.aclUrnAceGroupIdPut(this.encode(urn), groupId, role);
    console.assert(response.status == 204, response.statusText);
  }

  async getAvailableGroups(urn: string) {
    const response = await this.aclAPI.aclUrnAvailableGroupsGet(this.encode(urn));
    console.assert(response.status == 200, response.statusText);
    return response.data;
  }

  async removeACE(urn: string, groupId: string) {
    const response = await this.aclAPI.aclUrnAceGroupIdDelete(this.encode(urn), groupId);
    console.assert(response.status == 204, response.statusText);
  }

  async canModify(urn: string): Promise<boolean> {
    try {
      const response = await this.aclAPI.aclUrnCanModifyGet(this.encode(urn));
      return response.status == 204;
    } catch (e) {
      console.debug(e);
      return false;
    }
  }
}