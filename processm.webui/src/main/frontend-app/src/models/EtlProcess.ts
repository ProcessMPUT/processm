export default interface EtlProcess {
  id: string;
  name: string;
  type: EtlProcessType;
  dataConnectorId: string;
  isActive: boolean;
  lastExecutionTime: Date | undefined;
}

export enum EtlProcessType {
  Automatic = "automatic",
  Jdbc = "jdbc"
}
