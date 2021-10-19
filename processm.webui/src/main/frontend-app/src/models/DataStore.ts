export default interface DataStore {
  id: string;
  name: string;
  size?: number;
  createdAt?: string;
}

export interface DataConnector {
  id: string;
  name: string;
  lastConnectionStatus?: boolean;
  properties: { [key: string]: string };
}

export enum ConnectionType {
  PostgreSql = "PostgreSQL",
  SqlServer = "SQL Server",
  MySql = "MySQL",
  OracleDatabase = "Oracle Database",
  Db2 = "DB2"
}
