import {
  DataNode,
  DataLink
} from "@/components/workspace/causal-net/CausalNet";

export default interface WorkspaceComponent<
  TComponentData = CausalNetComponentData | KpiComponentData,
  TComponentCustomizationData = CausalNetComponentCustomizationData | object
> {
  id: string;
  name: string | undefined;
  type: ComponentType | undefined;
  data?: TComponentData;
  customizationData?: TComponentCustomizationData;
}

export enum ComponentType {
  Kpi = "kpi",
  CausalNet = "causalNet"
}

interface ComponentData {
  type?: ComponentType;
  query?: string;
}

export interface CausalNetComponentData extends ComponentData {
  nodes: Array<DataNode>;
  edges: Array<DataLink>;
}

export interface CausalNetComponentCustomizationData {
  layout: { id: string; x: number; y: number }[];
}

export interface KpiComponentData extends ComponentData {
  value: number;
}
