import Vue from "vue";
import Workspace from "@/models/Workspace";
import BaseService from "./BaseService";
import { LayoutElement, WorkspaceComponent } from "@/models/WorkspaceComponent";
import { AbstractComponent, Workspace as ApiWorkspace } from "@/openapi";

export default class WorkspaceService extends BaseService {
  public async getAll(): Promise<Array<Workspace>> {
    const response = await this.workspacesApi.getWorkspaces(this.currentOrganizationId);

    return response.data.reduce((workspaces: Workspace[], workspace: ApiWorkspace) => {
      if (workspace.id != null) {
        workspaces.push({ id: workspace.id, name: workspace.name });
      }

      return workspaces;
    }, []);
  }

  public async createWorkspace(name: string): Promise<Workspace> {
    const response = await this.workspacesApi.createWorkspace(this.currentOrganizationId, {
      name: name
    });
    const workspace = response.data;

    if (workspace.id == null) {
      throw new Error("The received workspace object should contain id");
    }

    return { id: workspace.id, name: workspace.name };
  }

  public async updateWorkspace(workspace: Workspace): Promise<boolean> {
    const response = await this.workspacesApi.updateWorkspace(this.currentOrganizationId, workspace.id, workspace);

    return response.status == 204;
  }

  public async removeWorkspace(workspaceId: string): Promise<void> {
    const response = await this.workspacesApi.deleteWorkspace(this.currentOrganizationId, workspaceId, {
      validateStatus: (status: number) => [204, 404].indexOf(status) >= 0
    });
  }

  public async getComponent(workspaceId: string, componentId: string): Promise<WorkspaceComponent> {
    const response = await this.workspacesApi.getWorkspaceComponent(this.currentOrganizationId, workspaceId, componentId);
    const apiComponent = response.data;

    if (apiComponent.id == null) {
      throw new Error("The received component object should contain id");
    }

    return new WorkspaceComponent(apiComponent);
  }

  public async addComponent(workspaceId: string, component: WorkspaceComponent) {
    const response = await this.workspacesApi.addOrUpdateWorkspaceComponent(this.currentOrganizationId, workspaceId, component.id, component);

    return response.status == 204;
  }

  public async updateComponent(workspaceId: string, componentId: string, component: WorkspaceComponent) {
    const response = await this.workspacesApi.addOrUpdateWorkspaceComponent(this.currentOrganizationId, workspaceId, componentId, component);

    return response.status == 204;
  }

  public async getComponentData(workspaceId: string, componentId: string): Promise<unknown> {
    const response = await this.workspacesApi.getWorkspaceComponentData(this.currentOrganizationId, workspaceId, componentId);

    return response.data;
  }

  public async getWorkspaceComponents(workspaceId: string) {
    const response = await this.workspacesApi.getWorkspaceComponents(this.currentOrganizationId, workspaceId);

    return response.data.reduce((components: WorkspaceComponent[], apiComponent: AbstractComponent) => {
      if (apiComponent.id != null) {
        components.push(new WorkspaceComponent(apiComponent));
      }

      return components;
    }, []);
  }

  public async updateLayout(workspaceId: string, updatedLayoutElements: Record<string, LayoutElement>) {
    const response = await this.workspacesApi.updateWorkspaceLayout(this.currentOrganizationId, workspaceId, { data: updatedLayoutElements });

    return response.status == 204;
  }

  public async removeComponent(workspaceId: string, componentId: string): Promise<void> {
    const response = await this.workspacesApi.removeWorkspaceComponent(this.currentOrganizationId, workspaceId, componentId);
  }

  private get currentOrganizationId() {
    return Vue.prototype.$sessionStorage.currentOrganization.id;
  }
}
