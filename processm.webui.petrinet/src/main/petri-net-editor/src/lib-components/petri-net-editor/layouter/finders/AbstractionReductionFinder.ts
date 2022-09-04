import { ReductionFinder } from '@/lib-components/petri-net-editor/layouter/interfaces/ReductionFinder';
import { PetriNetState } from '@/lib-components/petri-net-editor/model/PetriNetState';
import { Place } from '@/lib-components/petri-net-editor/model/Place';
import { Reduction } from '@/lib-components/petri-net-editor/layouter/interfaces/Reduction';
import { AbstractionReduction } from '@/lib-components/petri-net-editor/layouter/reductions/AbstractionReduction';
import { Transition } from '@/lib-components/petri-net-editor/model/Transition';

export class AbstractionReductionFinder implements ReductionFinder {
    find(state: PetriNetState): [Reduction | null, number] {
        let bestReduction: AbstractionReduction | null = null;
        let bestScore = 0.0;

        const arcs = state.getArcs();
        for (const arc of arcs) {
            const place = state.getElement(arc.outElementId);
            const transition = state.getElement(arc.inElementId);
            if (place instanceof Place && transition instanceof Transition) {
                const postTransitions = state.getPostTransitions(place);
                if (postTransitions.length != 1) {
                    continue;
                }

                const postPlaces = state.getPostPlaces(transition);

                const prePlaces = state.getPrePlaces(transition);
                if (prePlaces.length != 1) {
                    continue;
                }

                const preTransitions = state.getPreTransitions(place);
                const preTransitionPostPlaces = preTransitions
                    .flatMap(transition => state.getPostPlaces(transition))
                    .filter(place => postPlaces.indexOf(place) != -1);

                if (preTransitionPostPlaces.length != 0) {
                    continue;
                }

                const reduction = new AbstractionReduction(place, transition, preTransitions, postPlaces);
                const score = reduction.getScore(state);
                if (score >= this.getMaxScore()) {
                    return [reduction, score];
                }
                if (score > bestScore) {
                    bestScore = score;
                    bestReduction = reduction;
                }
            }
        }

        return [bestReduction, bestScore];
    }

    getMaxScore(): number {
        return AbstractionReduction.MAX_SCORE;
    }

}
