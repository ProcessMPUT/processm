export default interface EtlProcess {
  id: string;
  name: string;
  type: EtlProcessType;
  dataConnectorId: string;
  isActive: boolean;
}

export enum EtlProcessType {
  Automatic = "automatic"
}
