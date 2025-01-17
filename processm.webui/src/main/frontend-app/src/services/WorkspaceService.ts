import Vue from "vue";
import BaseService from "./BaseService";
import { LayoutElement, WorkspaceComponent } from "@/models/WorkspaceComponent";
import { AbstractComponent, ComponentType, Workspace as ApiWorkspace } from "@/openapi";

export default class WorkspaceService extends BaseService {
  public async getAll(): Promise<Array<ApiWorkspace>> {
    const response = await this.workspacesApi.getWorkspaces(this.currentOrganizationId);

    return response.data;
  }

  public async createWorkspace(name: string): Promise<ApiWorkspace> {
    const response = await this.workspacesApi.createWorkspace({
      organizationId: this.currentOrganizationId,
      name: name
    });
    const workspace = response.data;

    if (workspace.id == null) {
      throw new Error("The received workspace object should contain id");
    }

    return workspace;
  }

  public async updateWorkspace(workspace: ApiWorkspace): Promise<boolean> {
    const response = await this.workspacesApi.updateWorkspace(workspace.id!, workspace);

    return response.status == 204;
  }

  public async removeWorkspace(workspaceId: string): Promise<void> {
    const response = await this.workspacesApi.deleteWorkspace(workspaceId, {
      validateStatus: (status: number) => [204, 404].indexOf(status) >= 0
    });
  }

  public async getComponent(workspaceId: string, componentId: string): Promise<WorkspaceComponent> {
    const response = await this.workspacesApi.getWorkspaceComponent(workspaceId, componentId);
    const apiComponent = response.data;

    if (apiComponent.id == null) {
      throw new Error("The received component object should contain id");
    }

    return new WorkspaceComponent(apiComponent);
  }

  private get currentOrganizationId() {
    return Vue.prototype.$sessionStorage.currentOrganization?.id;
  }

  public async getComponentData(workspaceId: string, componentId: string): Promise<unknown> {
    const response = await this.workspacesApi.getWorkspaceComponentData(workspaceId, componentId);

    return response.data;
  }

  public async getComponentDataVariant(workspaceId: string, componentId: string, variantId: number): Promise<unknown> {
    const response = await this.workspacesApi.getWorkspaceComponentDataVariant(workspaceId, componentId, variantId);

    return response.data;
  }

  public async setComponentDataVariant(workspaceId: string, componentId: string, variantId: number): Promise<unknown> {
    const response = await this.workspacesApi.patchWorkspaceComponentData(workspaceId, componentId, variantId);

    return response.data;
  }

  public async getWorkspaceComponents(workspaceId: string) {
    const response = await this.workspacesApi.getWorkspaceComponents(workspaceId);

    return response.data.reduce((components: WorkspaceComponent[], apiComponent: AbstractComponent) => {
      if (apiComponent.id != null) {
        components.push(new WorkspaceComponent(apiComponent));
      }

      return components;
    }, []);
  }

  public async updateLayout(workspaceId: string, updatedLayoutElements: Record<string, LayoutElement>) {
    const response = await this.workspacesApi.updateWorkspaceLayout(workspaceId, { data: updatedLayoutElements });

    return response.status == 204;
  }

  public async removeComponent(workspaceId: string, componentId: string): Promise<void> {
    const response = await this.workspacesApi.removeWorkspaceComponent(workspaceId, componentId);
  }

  public async updateComponent(workspaceId: string, componentId: string, component: WorkspaceComponent) {
    const payload = Object.assign({}, component) as { data?: any };
    switch (component.type) {
      case "causalNet":
        payload.data = {
          nodes: [],
          edges: [],
          modelVersion: payload.data.modelVersion,
          type: payload.data.type ?? "causalNet"
        };
        break;
      case "directlyFollowsGraph":
      case "kpi":
        delete payload.data;
        break;
    }
    const response = await this.workspacesApi.addOrUpdateWorkspaceComponent(workspaceId, componentId, payload as AbstractComponent);

    return response.data;
  }

  public async getEmptyComponent(componentType: ComponentType) {
    const response = await this.workspacesApi.getEmptyComponent(componentType);
    return response.data;
  }
}
