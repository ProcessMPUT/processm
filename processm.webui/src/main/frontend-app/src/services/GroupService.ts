import axios from "axios";
import Workspace from "@/models/Workspace";
import BaseService from "./BaseService";

export default class GroupService extends BaseService {
  public async getUserGroups(): Promise<Array<Workspace>> {
    const response = await axios.get<{ data: Workspace[] }>("/api/workspaces");

    return response.data.data;
  }
}
