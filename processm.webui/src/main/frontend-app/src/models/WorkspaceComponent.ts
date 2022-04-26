import {
  DataLink,
  DataNode
} from "@/components/workspace/causal-net/CausalNet";
import { ComponentType } from "@/openapi";

type CustomizationData = {};

export interface CausalNetCustomizationData extends CustomizationData {
  layout: { id: string; x: number; y: number }[];
}

export abstract class ComponentData {
  constructor(init: Partial<ComponentData>) {
    Object.assign(this, init);
  }

  type!: ComponentType;

  abstract get isDisplayable(): boolean | undefined;
}

export class CausalNetComponentData extends ComponentData {
  nodes?: Array<DataNode>;
  edges?: Array<DataLink>;

  get isDisplayable() {
    return this.nodes != null && this.edges != null && this.nodes.length > 0;
  }
}

export class KpiComponentData extends ComponentData {
  value?: number;

  get isDisplayable() {
    return this.value != null;
  }
}

export class BPMNComponentData extends ComponentData {
  get isDisplayable(): boolean {
    return true; // TODO
  }
}

export class LayoutElement {
  constructor(init: Partial<LayoutElement>) {
    Object.assign(this, init);
  }

  x?: number;
  y?: number;
  width?: number;
  height?: number;
}

export class WorkspaceComponent {
  constructor(init: {
    id: string;
    name?: string;
    query: string;
    dataStore: string;
    type: ComponentType;
    data?: { type: ComponentType };
    layout?: LayoutElement;
    customizationData?: CustomizationData;
  }) {
    Object.assign(this, init);

    // FIXME: customizations like this should be implemented using inheritance
    switch (this.type) {
      case ComponentType.CausalNet: {
        this.data = new CausalNetComponentData(init.data ?? {});
        break;
      }
      case ComponentType.Kpi: {
        this.data = new KpiComponentData(init.data ?? {});
        break;
      }
      case ComponentType.Bpmn: {
        this.data = new BPMNComponentData(init.data ?? {});
      }
    }
  }

  id!: string;
  name?: string;
  query!: string;
  dataStore!: string;
  type!: ComponentType;
  data: ComponentData;
  layout?: LayoutElement;
  customizationData?: CustomizationData;
  dataLastModified?: string;
  userLastModified?: string;
  lastError?: string;
}
