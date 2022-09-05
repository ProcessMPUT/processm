import Vue from "vue";
import DataStore, { DataConnector } from "@/models/DataStore";
import BaseService from "./BaseService";
import {
  DataStore as ApiDataStore,
  DataConnector as ApiDataConnector,
  AbstractEtlProcess,
  EtlProcessType as ApiEtlProcessType,
  EtlProcess as ApiEtlProcess,
  CaseNotion as ApiCaseNotion,
  EtlProcessInfo
} from "@/openapi";
import EtlProcess, { EtlProcessType } from "@/models/EtlProcess";
import CaseNotion from "@/models/CaseNotion";
import JdbcEtlProcessConfiguration from "@/models/JdbcEtlProcessConfiguration";

export default class DataStoreService extends BaseService {
  private static get currentOrganizationId() {
    return Vue.prototype.$sessionStorage.currentOrganization.id;
  }

  public async getAll(): Promise<Array<DataStore>> {
    const response = await this.dataStoresApi.getDataStores(
      DataStoreService.currentOrganizationId
    );

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
    await this.dataStoresApi.deleteDataStore(
      DataStoreService.currentOrganizationId,
      dataStoreId,
      {
        validateStatus: (status: number) => [204, 404].indexOf(status) >= 0
      }
    );
  }

  public async getDataConnectors(
    dataStoreId: string
  ): Promise<Array<DataConnector>> {
    const response = await this.dataStoresApi.getDataConnectors(
      DataStoreService.currentOrganizationId,
      dataStoreId
    );

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
    await this.dataStoresApi.deleteDataConnector(
      DataStoreService.currentOrganizationId,
      dataStoreId,
      dataConnectorId,
      {
        validateStatus: (status: number) => [204, 404].indexOf(status) >= 0
      }
    );
  }

  public async testDataConnector(
    dataStoreId: string,
    dataConnectorConfiguration: Record<string, string>
  ): Promise<void> {
    await this.dataStoresApi.testDataConnector(
      DataStoreService.currentOrganizationId,
      dataStoreId,
      { data: { properties: dataConnectorConfiguration } }
    );
  }

  public async getCaseNotionSuggestions(
    dataStoreId: string,
    dataConnectorId: string
  ): Promise<CaseNotion[]> {
    const response = await this.dataStoresApi.getCaseNotionSuggestions(
      DataStoreService.currentOrganizationId,
      dataStoreId,
      dataConnectorId
    );

    return response.data.data.reduce(
      (caseNotions: CaseNotion[], caseNotion: ApiCaseNotion) => {
        if (caseNotion != null) {
          caseNotions.push({
            classes: new Map(Object.entries(caseNotion.classes)),
            edges: caseNotion.edges
          });
        }

        return caseNotions;
      },
      []
    );
  }

  public async getRelationshipGraph(
    dataStoreId: string,
    dataConnectorId: string
  ): Promise<CaseNotion> {
    const response = await this.dataStoresApi.getRelationshipGraph(
      DataStoreService.currentOrganizationId,
      dataStoreId,
      dataConnectorId
    );

    const caseNotion = response.data.data;

    return {
      classes: new Map(Object.entries(caseNotion.classes)),
      edges: caseNotion.edges
    };
  }

  public async getEtlProcesses(dataStoreId: string): Promise<EtlProcess[]> {
    const response = await this.dataStoresApi.getEtlProcesses(
      DataStoreService.currentOrganizationId,
      dataStoreId
    );

    const etlProcesses = response.data.data.reduce(
      (etlProcesses: EtlProcess[], etlProcess: ApiEtlProcess) => {
        if (etlProcess.dataConnectorId == null || etlProcess.type == null) {
          console.error(
            "Received an ETL process object without required values"
          );
          return etlProcesses;
        }
        if (etlProcess.id != null) {
          etlProcesses.push({
            id: etlProcess.id,
            name: etlProcess.name || "",
            type: etlProcess.type,
            dataConnectorId: etlProcess.dataConnectorId,
            isActive: etlProcess.isActive || false,
            lastExecutionTime: etlProcess.lastExecutionTime
              ? new Date(etlProcess.lastExecutionTime)
              : undefined
          });
        }

        return etlProcesses;
      },
      []
    );

    return etlProcesses;
  }

  public async createEtlProcess(
    dataStoreId: string,
    processName: string,
    processType: EtlProcessType,
    dataConnectorId: string,
    configuration: CaseNotion | JdbcEtlProcessConfiguration
  ): Promise<AbstractEtlProcess> {
    let data: AbstractEtlProcess;
    if (processType == EtlProcessType.Automatic) {
      data = {
        name: processName,
        dataConnectorId,
        type: processType as ApiEtlProcessType,
        caseNotion: {
          classes: Object.fromEntries((configuration as CaseNotion).classes),
          edges: (configuration as CaseNotion).edges
        }
      };
    } else {
      data = {
        name: processName,
        dataConnectorId,
        type: processType as ApiEtlProcessType,
        configuration: configuration as JdbcEtlProcessConfiguration
      };
    }
    const response = await this.dataStoresApi.createEtlProcess(
      DataStoreService.currentOrganizationId,
      dataStoreId,
      {
        data: data
      }
    );

    return response.data.data;
  }

  public async changeEtlProcessActivationState(
    dataStoreId: string,
    etlProcessId: string,
    isActive: boolean
  ) {
    const response = await this.dataStoresApi.updateEtlProcess(
      DataStoreService.currentOrganizationId,
      dataStoreId,
      etlProcessId,
      { data: { isActive } }
    );

    return response.status == 204;
  }
  
  public async createSamplingJdbcEtlProcess(
    dataStoreId: string,
    processName: string,
    dataConnectorId: string,
    configuration: CaseNotion | JdbcEtlProcessConfiguration
  ): Promise<AbstractEtlProcess> {
    const data: AbstractEtlProcess = {
      name: processName,
      dataConnectorId,
      type: ApiEtlProcessType.Jdbc,
      configuration: configuration as JdbcEtlProcessConfiguration
    };
    const response = await this.dataStoresApi.createSamplingJdbcEtlProcess(
      DataStoreService.currentOrganizationId,
      dataStoreId,
      {
        data: data
      }
    );

    return response.data.data;
  }

  public async getEtlProcessInfo(
    dataStoreId: string,
    etlProcessId: string
  ): Promise<EtlProcessInfo> {
    const response = await this.dataStoresApi.getEtlProcess(
      DataStoreService.currentOrganizationId,
      dataStoreId,
      etlProcessId
    );
    return response.data;
  }

  public async removeEtlProcess(
    dataStoreId: string,
    etlProcessId: string
  ): Promise<void> {
    await this.dataStoresApi.deleteEtlProcess(
      DataStoreService.currentOrganizationId,
      dataStoreId,
      etlProcessId,
      {
        validateStatus: (status: number) => [204, 404].indexOf(status) >= 0
      }
    );
  }

  public async recreateXesLogFromEtlProcess(
    dataStoreId: string,
    etlProcessId: string
  ): Promise<void> {
    await this.dataStoresApi.recreateLogFromEtlProcess(
      DataStoreService.currentOrganizationId,
      dataStoreId,
      etlProcessId
    );
  }
  
  public async removeLog(
    dataStoreId: string,
    logIdentityId: string
  ): Promise<void> {
    await this.dataStoresApi.removeLog(dataStoreId, logIdentityId, {
      validateStatus: (status: number) => [204, 404].indexOf(status) >= 0
    });
  }
}
