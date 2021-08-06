import Vue from "vue";
import DataStore from "@/models/DataStore";
import BaseService from "./BaseService";
import { DataStore as ApiDataStore } from "@/openapi";
import DateTimeFormat = Intl.DateTimeFormat;

export default class DataStoreService extends BaseService {
  private static get currentOrganizationId() {
    return Vue.prototype.$sessionStorage.currentOrganization.id;
  }

  public async getAll(): Promise<Array<DataStore>> {
    const response = await this.dataStoresApi.getDataStores(
      DataStoreService.currentOrganizationId
    );

    this.ensureSuccessfulResponseCode(response);

    const dataStores = response.data.data.reduce(
      (dataStores: DataStore[], dataStore: ApiDataStore) => {
        if (dataStore.id != null) {
          dataStores.push({
            id: dataStore.id,
            name: dataStore.name,
            createdAt: new Date(dataStore.createdAt!).toLocaleString()
          });
        }

        return dataStores;
      },
      []
    );

    if (dataStores.length > 0) {
      Vue.prototype.$sessionStorage.defaultDataStoreId = dataStores[0].id;
    }

    return dataStores;
  }

  public async createDataStore(name: string): Promise<DataStore> {
    const response = await this.dataStoresApi.createDataStore(
      DataStoreService.currentOrganizationId,
      {
        data: {
          name: name
        }
      }
    );

    this.ensureSuccessfulResponseCode(response);
    const dataStore = response.data.data;

    return {
      id: dataStore.id!,
      name: dataStore.name,
      createdAt: new Date(dataStore.createdAt!).toLocaleString()
    };
  }
}
