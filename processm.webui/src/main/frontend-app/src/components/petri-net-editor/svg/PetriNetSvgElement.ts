import { PetriNetElement } from "@/components/petri-net-editor/model/PetriNetElement";

export abstract class PetriNetSvgElement {
  readonly model: PetriNetElement;

  protected constructor(element: PetriNetElement) {
    this.model = element;
  }

  abstract delete(): void;
}
