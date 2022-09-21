import { PetriNetElement } from "@/components/petri-net-editor/model/PetriNetElement";

export abstract class PetriNetSvgElement {
  readonly model: PetriNetElement;

  protected _scaleFactor = 1;

  protected constructor(element: PetriNetElement) {
    this.model = element;
  }

  abstract delete(): void;

  abstract set scaleFactor(value: number);
}
