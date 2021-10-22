import Vue from "vue";
import DataStore, { DataConnector } from "@/models/DataStore";
import BaseService from "./BaseService";
import {
  DataStore as ApiDataStore,
  DataConnector as ApiDataConnector
} from "@/openapi";

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
            createdAt:
              dataStore.createdAt != null
                ? new Date(dataStore.createdAt).toLocaleString()
                : undefined
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

  public async getDataStore(dataStoreId: string): Promise<DataStore> {
    const response = await this.dataStoresApi.getDataStore(
      DataStoreService.currentOrganizationId,
      dataStoreId
    );

    this.ensureSuccessfulResponseCode(response);
    const dataStore = response.data.data;

    if (dataStore.id == null) throw new Error("DataStoreId is undefined");

    return {
      id: dataStore.id,
      name: dataStore.name,
      size: dataStore.size,
      createdAt:
        dataStore.createdAt != null
          ? new Date(dataStore.createdAt).toLocaleString()
          : undefined
    };
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

    if (dataStore.id == null) throw new Error("DataStoreId is undefined");

    return {
      id: dataStore.id,
      name: dataStore.name,
      createdAt:
        dataStore.createdAt != null
          ? new Date(dataStore.createdAt).toLocaleString()
          : undefined
    };
  }

  public async updateDataStore(dataStoreId: string, dataStore: DataStore) {
    const response = await this.dataStoresApi.updateDataStore(
      DataStoreService.currentOrganizationId,
      dataStoreId,
      { data: dataStore }
    );

    return response.status == 204;
  }

  public async removeDataStore(dataStoreId: string): Promise<void> {
    const response = await this.dataStoresApi.deleteDataStore(
      DataStoreService.currentOrganizationId,
      dataStoreId
    );

    this.ensureSuccessfulResponseCode(response, 204, 404);
  }

  public async getDataConnectors(
    dataStoreId: string
  ): Promise<Array<DataConnector>> {
    const response = await this.dataStoresApi.getDataConnectors(
      DataStoreService.currentOrganizationId,
      dataStoreId
    );

    this.ensureSuccessfulResponseCode(response);

    return response.data.data.reduce(
      (dataConnectors: DataConnector[], dataConnector: ApiDataConnector) => {
        if (dataConnector.id != null) {
          dataConnectors.push({
            id: dataConnector.id,
            name: dataConnector.name || "",
            lastConnectionStatus: dataConnector.lastConnectionStatus,
            lastConnectionStatusTimestamp:
              dataConnector.lastConnectionStatusTimestamp,
            properties: dataConnector.properties || {}
          });
        }

        return dataConnectors;
      },
      []
    );
  }

  public async createDataConnector(
    dataStoreId: string,
    dataConnectorName: string,
    dataConnectorConfiguration: Record<string, string>
  ): Promise<DataConnector> {
    const response = await this.dataStoresApi.createDataConnector(
      DataStoreService.currentOrganizationId,
      dataStoreId,
      {
        data: {
          name: dataConnectorName,
          properties: dataConnectorConfiguration
        }
      }
    );

    this.ensureSuccessfulResponseCode(response);
    const dataConnector = response.data.data;

    if (dataConnector.id == null)
      throw new Error("DataConnectorId is undefined");

    return {
      id: dataConnector.id,
      name: dataConnector.name || "",
      lastConnectionStatus: dataConnector.lastConnectionStatus,
      properties: dataConnector.properties || {}
    };
  }

  public async updateDataConnector(
    dataStoreId: string,
    dataConnectorId: string,
    dataConnector: DataConnector
  ) {
    const response = await this.dataStoresApi.updateDataConnector(
      DataStoreService.currentOrganizationId,
      dataStoreId,
      dataConnectorId,
      { data: dataConnector }
    );

    return response.status == 204;
  }

  public async removeDataConnector(
    dataStoreId: string,
    dataConnectorId: string
  ): Promise<void> {
    const response = await this.dataStoresApi.deleteDataConnector(
      DataStoreService.currentOrganizationId,
      dataStoreId,
      dataConnectorId
    );

    this.ensureSuccessfulResponseCode(response, 204, 404);
  }

  public async testDataConnector(
    dataStoreId: string,
    dataConnectorConfiguration: Record<string, string>
  ): Promise<boolean> {
    const response = await this.dataStoresApi.testDataConnector(
      DataStoreService.currentOrganizationId,
      dataStoreId,
      { data: { properties: dataConnectorConfiguration } }
    );

    this.ensureSuccessfulResponseCode(response);

    return response.data.data.isValid;
  }
}
