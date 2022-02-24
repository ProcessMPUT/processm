export default interface EtlProcess {
  id: string;
  name: string;
  type: EtlProcessType;
  dataConnectorId: string;
}

export enum EtlProcessType {
  Automatic = "automatic",
  Jdbc = "jdbc"
}
