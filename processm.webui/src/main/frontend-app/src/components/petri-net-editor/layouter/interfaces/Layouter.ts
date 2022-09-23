import { PetriNetState } from "@/components/petri-net-editor/model/PetriNetState";

export interface Layouter {
  run(state: PetriNetState): PetriNetState;

  clearOverlay(): void;
}
