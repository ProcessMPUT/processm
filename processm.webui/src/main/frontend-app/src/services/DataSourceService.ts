import Vue from "vue";
import DataSource from "@/models/DataSource";
import BaseService from "./BaseService";
import { DataSource as ApiDataSource } from "@/openapi";
import DateTimeFormat = Intl.DateTimeFormat;

export default class DataSourceService extends BaseService {
  private static get currentOrganizationId() {
    return Vue.prototype.$sessionStorage.currentOrganization.id;
  }

  public async getAll(): Promise<Array<DataSource>> {
    const response = await this.dataSourcesApi.getDataSources(
      DataSourceService.currentOrganizationId
    );

    this.ensureSuccessfulResponseCode(response);

    return response.data.data.reduce(
      (dataSources: DataSource[], dataSource: ApiDataSource) => {
        if (dataSource.id != null) {
          dataSources.push({
            id: dataSource.id,
            name: dataSource.name,
            createdAt: new Date(dataSource.createdAt!).toLocaleString()
          });
        }

        return dataSources;
      },
      []
    );
  }

  public async createDataStore(name: string): Promise<DataSource> {
    const response = await this.dataSourcesApi.createDataSource(
      DataSourceService.currentOrganizationId,
      {
        data: {
          name: name
        }
      }
    );

    this.ensureSuccessfulResponseCode(response);
    const dataSource = response.data.data;

    return {
      id: dataSource.id!,
      name: dataSource.name,
      createdAt: new Date(dataSource.createdAt!).toLocaleString()
    };
  }
}