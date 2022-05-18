export default interface EtlProcess {
  id: string;
  name: string;
  type: EtlProcessType;
  dataConnectorId: string;
  lastExecutionTime: Date | undefined;
}

export enum EtlProcessType {
  Automatic = "automatic",
  Jdbc = "jdbc"
}
