import axios from "axios";
import Workspace from "@/models/Workspace";

export default class WorkspaceService {
  public async getAll(): Promise<Array<Workspace>> {
    const response = await axios.get<{ data: Workspace[] }>("/api/workspaces");

    return response.data.data;
  }

  public async create(name: string): Promise<Workspace> {
    const response = await axios.post<{ data: Workspace }>("/api/workspaces", {
      name: name
    });

    return response.data.data;
  }

  public async update(workspace: Workspace): Promise<Workspace> {
    const response = await axios.patch<{ data: Workspace }>(
      `/api/workspaces/${workspace.id}`,
      workspace
    );

    return response.data.data;
  }

  public async remove(id: number): Promise<boolean> {
    const response = await axios.delete(`/api/workspaces/${id}`);

    return [204, 404].includes(response.status);
  }
}
