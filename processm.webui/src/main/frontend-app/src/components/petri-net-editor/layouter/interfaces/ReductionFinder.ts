import { PetriNetState } from '@/components/petri-net-editor/model/PetriNetState';
import { Reduction } from '@/components/petri-net-editor/layouter/interfaces/Reduction';

export interface ReductionFinder {
    find(state: PetriNetState): [Reduction | null, number];

    getMaxScore(): number;
}
