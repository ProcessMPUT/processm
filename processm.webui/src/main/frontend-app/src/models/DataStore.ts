import { ConnectionProperties } from "@/openapi";

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
  lastConnectionStatusTimestamp?: string;
  connectionProperties: ConnectionProperties;
}

export enum ConnectionType {
  PostgreSql = "PostgreSQL",
  SqlServer = "SQL Server",
  MySql = "MySQL",
  OracleDatabase = "Oracle Database",
  Db2 = "DB2",
  CouchDBProperties = "CouchDB",
  MongoDBProperties = "MongoDB",
  CouchDBString = "CouchDBString",
  MongoDBString = "MongoDBString",
  JdbcString = "JdbcString"
}
