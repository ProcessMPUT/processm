import BaseService from "@/services/BaseService";
import { Config } from "@/openapi";

export default class ConfigService extends BaseService {
  private config?: Config;

  public async getConfig() {
    if (this.config === undefined) {
      const response = await this.configApi.getConfig();
      if (response.status != 200) {
        throw new Error(response.statusText);
      }
      this.config = response.data;
    }
    return this.config;
  }
}
