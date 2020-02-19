import axios from "axios";
import Workspace from "@/models/Workspace";

export default class WorkspaceService {
  // this is tmp only
  private userId = 1;

  public async getAll(): Promise<Array<Workspace>> {
    const response = await axios.get<{ data: Workspace[] }>(
      `/api/${this.userId}/workspaces`
    );

    return response.data.data;
  }

  public async create(name: string): Promise<Workspace> {
    const response = await axios.post<{ data: Workspace }>(
      `/api/${this.userId}/workspaces`,
      {
        name: name
      }
    );

    return response.data.data;
  }

  public async update(workspace: Workspace): Promise<Workspace> {
    const response = await axios.patch<{ data: Workspace }>(
      `/api/${this.userId}/workspaces/${workspace.id}`,
      workspace
    );

    return response.data.data;
  }

  public async remove(id: number): Promise<boolean> {
    const response = await axios.delete(`/api/${this.userId}/workspaces/${id}`);

    return response.status == 204;
  }
}
