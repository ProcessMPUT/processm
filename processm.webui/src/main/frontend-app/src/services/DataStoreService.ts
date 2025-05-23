import Vue from "vue";
import DataStore, { DataConnector } from "@/models/DataStore";
import BaseService from "./BaseService";
import {
  AbstractEtlProcess,
  CaseNotion,
  CaseNotion as ApiCaseNotion,
  ConnectionProperties,
  DataConnector as ApiDataConnector,
  DataStore as ApiDataStore,
  EtlProcess,
  EtlProcess as ApiEtlProcess,
  EtlProcessInfo,
  EtlProcessType,
  EtlProcessType as ApiEtlProcessType,
  RelationshipGraph
} from "@/openapi";
import JdbcEtlProcessConfiguration from "@/models/JdbcEtlProcessConfiguration";

export default class DataStoreService extends BaseService {
  public async getAll(): Promise<Array<DataStore>> {
    const response = await this.dataStoresApi.getDataStores();

    const dataStores = response.data.reduce((dataStores: DataStore[], dataStore: ApiDataStore) => {
      if (dataStore.id != null) {
        dataStores.push({
          id: dataStore.id,
          name: dataStore.name,
          createdAt: dataStore.createdAt != null ? new Date(dataStore.createdAt).toLocaleString() : undefined
        });
      }

      return dataStores;
    }, []);

    if (dataStores.length > 0) {
      Vue.prototype.$sessionStorage.defaultDataStoreId = dataStores[0].id;
    }

    return dataStores;
  }

  public async getDataStore(dataStoreId: string): Promise<DataStore> {
    const response = await this.dataStoresApi.getDataStore(dataStoreId);

    const dataStore = response.data;

    if (dataStore.id == null) throw new Error("DataStoreId is undefined");

    return {
      id: dataStore.id,
      name: dataStore.name,
      size: dataStore.size,
      createdAt: dataStore.createdAt != null ? new Date(dataStore.createdAt).toLocaleString() : undefined
    };
  }

  public async createDataStore(name: string): Promise<DataStore> {
    const response = await this.dataStoresApi.createDataStore({
      name: name
    });

    const dataStore = response.data;

    if (dataStore.id == null) throw new Error("DataStoreId is undefined");

    return {
      id: dataStore.id,
      name: dataStore.name,
      createdAt: dataStore.createdAt != null ? new Date(dataStore.createdAt).toLocaleString() : undefined
    };
  }

  public async updateDataStore(dataStoreId: string, dataStore: DataStore) {
    const response = await this.dataStoresApi.updateDataStore(dataStoreId, dataStore);

    return response.status == 204;
  }

  public async removeDataStore(dataStoreId: string): Promise<void> {
    await this.dataStoresApi.deleteDataStore(dataStoreId, {
      validateStatus: (status: number) => [204, 404].indexOf(status) >= 0
    });
  }

  public async getDataConnectors(dataStoreId: string): Promise<Array<DataConnector>> {
    const response = await this.dataStoresApi.getDataConnectors(dataStoreId);

    return response.data.reduce((dataConnectors: DataConnector[], dataConnector: ApiDataConnector) => {
      if (dataConnector.id != null) {
        dataConnectors.push({
          id: dataConnector.id,
          name: dataConnector.name || "",
          lastConnectionStatus: dataConnector.lastConnectionStatus,
          lastConnectionStatusTimestamp: dataConnector.lastConnectionStatusTimestamp,
          connectionProperties: dataConnector.connectionProperties!
        });
      }

      return dataConnectors;
    }, []);
  }

  public async createDataConnector(dataStoreId: string, dataConnectorName: string, dataConnectorConfiguration: ConnectionProperties): Promise<DataConnector> {
    const response = await this.dataStoresApi.createDataConnector(dataStoreId, {
      name: dataConnectorName,
      connectionProperties: dataConnectorConfiguration
    });

    const dataConnector = response.data;

    if (dataConnector.id == null) throw new Error("DataConnectorId is undefined");

    return {
      id: dataConnector.id,
      name: dataConnector.name || "",
      lastConnectionStatus: dataConnector.lastConnectionStatus,
      connectionProperties: Object.assign({}, dataConnector.connectionProperties)
    };
  }

  public async updateDataConnector(dataStoreId: string, dataConnectorId: string, dataConnector: ApiDataConnector) {
    const response = await this.dataStoresApi.updateDataConnector(dataStoreId, dataConnectorId, dataConnector);

    return response.status == 204;
  }

  public async removeDataConnector(dataStoreId: string, dataConnectorId: string): Promise<void> {
    await this.dataStoresApi.deleteDataConnector(dataStoreId, dataConnectorId, {
      validateStatus: (status: number) => [204, 404].indexOf(status) >= 0
    });
  }

  public async testDataConnector(dataStoreId: string, dataConnectorConfiguration: ConnectionProperties): Promise<void> {
    await this.dataStoresApi.testDataConnector(dataStoreId, { connectionProperties: dataConnectorConfiguration });
  }

  public async getCaseNotionSuggestions(dataStoreId: string, dataConnectorId: string): Promise<CaseNotion[]> {
    const response = await this.dataStoresApi.getCaseNotionSuggestions(dataStoreId, dataConnectorId);

    return response.data.reduce((caseNotions: CaseNotion[], caseNotion: ApiCaseNotion) => {
      if (caseNotion != null) {
        caseNotions.push(caseNotion);
      }

      return caseNotions;
    }, []);
  }

  public async getRelationshipGraph(dataStoreId: string, dataConnectorId: string): Promise<RelationshipGraph> {
    const response = await this.dataStoresApi.getRelationshipGraph(dataStoreId, dataConnectorId);

    return response.data;
  }

  public async getEtlProcesses(dataStoreId: string): Promise<EtlProcess[]> {
    const response = await this.dataStoresApi.getEtlProcesses(dataStoreId);

    const etlProcesses = response.data.reduce((etlProcesses: EtlProcess[], etlProcess: ApiEtlProcess) => {
      if (etlProcess.dataConnectorId == null || etlProcess.type == null) {
        console.error("Received an ETL process object without required values");
        return etlProcesses;
      }
      if (etlProcess.id != null) {
        etlProcesses.push(etlProcess);
      }

      return etlProcesses;
    }, []);

    return etlProcesses;
  }

  public async saveEtlProcess(
    dataStoreId: string,
    processName: string,
    processType: EtlProcessType,
    dataConnectorId: string,
    configuration: CaseNotion | JdbcEtlProcessConfiguration,
    etlId?: string
  ): Promise<AbstractEtlProcess> {
    let data: AbstractEtlProcess;
    if (processType == EtlProcessType.Automatic) {
      data = {
        id: etlId,
        name: processName,
        dataConnectorId,
        type: processType,
        caseNotion: configuration as CaseNotion
      };
    } else {
      data = {
        id: etlId,
        name: processName,
        dataConnectorId,
        type: processType as ApiEtlProcessType,
        configuration: configuration as JdbcEtlProcessConfiguration
      };
    }
    if (etlId === undefined) {
      const response = await this.dataStoresApi.createEtlProcess(dataStoreId, data);
      return response.data;
    }

    await this.dataStoresApi.updateEtlProcess(dataStoreId, etlId, data);
    return data;
  }

  public async changeEtlProcessActivationState(dataStoreId: string, etlProcessId: string, isActive: boolean) {
    const response = await this.dataStoresApi.patchEtlProcess(dataStoreId, etlProcessId, { isActive: isActive } as AbstractEtlProcess);

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
    const response = await this.dataStoresApi.createSamplingJdbcEtlProcess(dataStoreId, data);

    return response.data as AbstractEtlProcess;
  }

  public async getEtlProcessInfo(dataStoreId: string, etlProcessId: string): Promise<EtlProcessInfo> {
    const response = await this.dataStoresApi.getEtlProcess(dataStoreId, etlProcessId);
    return response.data;
  }

  public async removeEtlProcess(dataStoreId: string, etlProcessId: string): Promise<void> {
    await this.dataStoresApi.deleteEtlProcess(dataStoreId, etlProcessId, {
      validateStatus: (status: number) => [204, 404].indexOf(status) >= 0
    });
  }

  public async removeLog(dataStoreId: string, logIdentityId: string): Promise<void> {
    await this.dataStoresApi.removeLog(dataStoreId, logIdentityId, {
      validateStatus: (status: number) => [204, 404].indexOf(status) >= 0
    });
  }

  public async triggerEtlProcess(dataStoreId: string, etlProcessId: string): Promise<void> {
    await this.dataStoresApi.triggerEtlProcess(dataStoreId, etlProcessId, {
      validateStatus: (status: number) => status == 204
    });
  }
}
