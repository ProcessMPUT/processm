import Vue from "vue";
import Workspace from "@/models/Workspace";
import BaseService from "./BaseService";
import WorkspaceComponent from "@/models/WorkspaceComponent";
import { Workspace as ApiWorkspace, ComponentAbstract } from "@/openapi/model";

export default class WorkspaceService extends BaseService {
  public async getAll(): Promise<Array<Workspace>> {
    const response = await this.workspacesApi.getWorkspaces(
      this.currentOrganizationId
    );

    this.ensureSuccessfulResponseCode(response);

    return response.data.data.reduce(
      (workspaces: Workspace[], workspace: ApiWorkspace) => {
        if (workspace.id != null) {
          workspaces.push({ id: workspace.id, name: workspace.name });
        }

        return workspaces;
      },
      []
    );
  }

  public async createWorkspace(name: string): Promise<Workspace> {
    const response = await this.workspacesApi.createWorkspace(
      this.currentOrganizationId,
      {
        data: { name }
      }
    );
    const workspace = response.data.data;

    this.ensureSuccessfulResponseCode(response);

    if (workspace.id == null) {
      throw new Error("The received workspace object should contain id");
    }

    return { id: workspace.id, name: workspace.name };
  }

  public async updateWorkspace(workspace: Workspace): Promise<boolean> {
    const response = await this.workspacesApi.updateWorkspace(
      this.currentOrganizationId,
      workspace.id,
      { data: workspace }
    );

    return response.status == 204;
  }

  public async removeWorkspace(workspaceId: string): Promise<void> {
    const response = await this.workspacesApi.deleteWorkspace(
      this.currentOrganizationId,
      workspaceId
    );

    this.ensureSuccessfulResponseCode(response, 204, 404);
  }

  public async getComponent(
    workspaceId: string,
    componentId: string
  ): Promise<WorkspaceComponent> {
    const response = await this.workspacesApi.getWorkspaceComponent(
      this.currentOrganizationId,
      workspaceId,
      componentId
    );
    const component = response.data.data;

    this.ensureSuccessfulResponseCode(response);

    if (component.id == null) {
      throw new Error("The received component object should contain id");
    }

    return {
      id: component.id,
      name: component.name,
      type: component.type,
      data: component.data
    };
  }

  public async getComponentData(
    workspaceId: string,
    componentId: string
  ): Promise<{ type?: string; query?: string }> {
    const response = await this.workspacesApi.getWorkspaceComponentData(
      this.currentOrganizationId,
      workspaceId,
      componentId
    );

    this.ensureSuccessfulResponseCode(response);

    return response.data.data;
  }

  public async getWorkspaceComponents(workspaceId: string) {
    const response = await this.workspacesApi.getWorkspaceComponents(
      this.currentOrganizationId,
      workspaceId
    );

    this.ensureSuccessfulResponseCode(response);

    return response.data.data.reduce(
      (components: WorkspaceComponent[], apiComponent: ComponentAbstract) => {
        if (apiComponent.id != null) {
          components.push({
            id: apiComponent.id,
            name: apiComponent.name,
            type: apiComponent.type,
            data: apiComponent.data
          });
        }

        return components;
      },
      []
    );
  }

  private get currentOrganizationId() {
    return Vue.prototype.$sessionStorage.currentOrganization.id;
  }
}
