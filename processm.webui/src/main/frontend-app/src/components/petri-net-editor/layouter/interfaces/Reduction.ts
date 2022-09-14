import { PetriNetState } from '@/components/petri-net-editor/model/PetriNetState';
import { Block } from '@/components/petri-net-editor/layouter/blocks/Block';

export interface Reduction {
    apply(state: PetriNetState): Block;

    getScore(state: PetriNetState): number;
}
