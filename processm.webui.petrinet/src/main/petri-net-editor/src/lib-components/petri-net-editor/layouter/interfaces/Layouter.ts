import { PetriNetState } from '@/lib-components/petri-net-editor/model/PetriNetState';

export interface Layouter {
    run(state: PetriNetState): PetriNetState;
    clearOverlay(): void;
}
