// TODO add PetriNet = "petriNet" to ComponentType
import {
  AbstractComponent,
  Alignment,
  BPMNComponent,
  CausalNetComponent,
  ComponentType,
  CustomProperty,
  DirectlyFollowsGraphComponent,
  KpiComponent,
  PetriNetComponent
} from "@/openapi";
import { CNetNodeConfig } from "@/components/Graph.vue";
import { EdgeConfig } from "@antv/g6-core/lib/types";

export abstract class CustomizationData {
  constructor(init: Partial<CustomizationData>) {
    Object.assign(this, init);
  }
}

export class ProcessModelCustomizationData extends CustomizationData {
  layout?: { id: string; x: number; y: number }[];
}

export abstract class ComponentData {
  constructor(init: Partial<ComponentData>) {
    Object.assign(this, init);
  }

  type!: ComponentType;

  abstract get isDisplayable(): boolean | undefined;
}

export class CNetComponentData extends ComponentData {
  nodes!: Array<CNetNodeConfig>;
  edges!: Array<EdgeConfig>;
  alignments?: Array<Alignment>;

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

export class PetriNetComponentData extends ComponentData {
  // TODO: Change any to some type
  places?: Array<any>;
  transitions?: Array<any>;
  initialMarking?: { [key: string]: number };
  finalMarking?: { [key: string]: number };

  get isDisplayable() {
    return this.initialMarking !== undefined && this.finalMarking !== undefined && this?.places?.length != 0 && this.transitions?.length != 0;
  }
}

export class DirectlyFollowsGraphData extends ComponentData {
  get isDisplayable() {
    return true;
  }
}

export class TreeLogViewComponentData extends ComponentData {
  get isDisplayable() {
    return true;
  }
}

export class FlatLogViewComponentData extends ComponentData {
  get isDisplayable() {
    return true;
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
  constructor(init: AbstractComponent) {
    Object.assign(this, init);

    // FIXME: customizations like this should be implemented using inheritance
    switch (this.type) {
      case ComponentType.CausalNet: {
        this.data = new CNetComponentData((init as CausalNetComponent).data ?? {});
        break;
      }
      case ComponentType.Kpi: {
        this.data = new KpiComponentData((init as KpiComponent).data ?? {});
        break;
      }
      case ComponentType.PetriNet: {
        this.data = new PetriNetComponentData((init as PetriNetComponent).data ?? {});
        this.customizationData = new ProcessModelCustomizationData((init as PetriNetComponent).customizationData ?? {});
        break;
      }
      case ComponentType.Bpmn: {
        this.data = new BPMNComponentData((init as BPMNComponent).data ?? {});
        break;
      }
      case ComponentType.TreeLogView: {
        this.data = new TreeLogViewComponentData({});
        break;
      }
      case ComponentType.FlatLogView: {
        this.data = new FlatLogViewComponentData({});
        break;
      }
      case ComponentType.DirectlyFollowsGraph: {
        this.data = new DirectlyFollowsGraphData((init as DirectlyFollowsGraphComponent).data ?? {});
        break;
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
  customProperties!: CustomProperty[];
}
