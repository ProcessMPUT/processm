import { PetriNetState } from '@/lib-components/petri-net-editor/model/PetriNetState';
import { Reduction } from '@/lib-components/petri-net-editor/layouter/interfaces/Reduction';

export interface ReductionFinder {
    find(state: PetriNetState): [Reduction | null, number];

    getMaxScore(): number;
}
