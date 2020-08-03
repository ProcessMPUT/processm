import {
  DataNode,
  DataLink
} from "@/components/workspace/causal-net/CausalNet";

export default interface WorkspaceComponent {
  id: string;
  name: string;
  type: ComponentType;
  data: CausalNetComponentData | KpiComponentData | undefined;
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
  layout?: Array<{ id: string; x: number; y: number }>;
}

export interface KpiComponentData extends ComponentData {
  value: number;
}
