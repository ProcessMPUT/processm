import { ReductionFinder } from '@/lib-components/petri-net-editor/layouter/interfaces/ReductionFinder';
import { PetriNetState } from '@/lib-components/petri-net-editor/model/PetriNetState';
import { Reduction } from '@/lib-components/petri-net-editor/layouter/interfaces/Reduction';
import { ShortLoopReduction } from '@/lib-components/petri-net-editor/layouter/reductions/ShortLoopReduction';

export class ShortLoopReductionFinder implements ReductionFinder {
    find(state: PetriNetState): [Reduction | null, number] {
        let reduction: ShortLoopReduction | null;
        reduction = this.findReduciblePlace(state);

        if (reduction == null) {
            reduction = this.findReducibleTransition(state);
        }

        return [reduction, reduction != null ? reduction.getScore(state) : 0.0];
    }

    getMaxScore(): number {
        return ShortLoopReduction.MAX_SCORE;
    }

    private findReduciblePlace(state: PetriNetState): ShortLoopReduction | null {
        for (const placeToReduce of state.getPlaces()) {
            const postTransitions = state.getPostTransitions(placeToReduce);
            if (postTransitions.length != 1) {
                continue;
            }

            const transitionCandidate = postTransitions.find(transition => {
                return state.getPostPlaces(transition)
                    .find(place => place == placeToReduce) != undefined;
            });

            if (transitionCandidate == undefined) {
                continue;
            }

            return new ShortLoopReduction(placeToReduce, transitionCandidate);
        }

        return null;
    }

    private findReducibleTransition(state: PetriNetState): ShortLoopReduction | null {
        for (const transitionToReduce of state.getTransitions()) {
            const postPlaces = state.getPostPlaces(transitionToReduce);
            if (postPlaces.length != 1) {
                continue;
            }

            const placeCandidate = postPlaces.find(place => {
                return state.getPostTransitions(place)
                    .find(transition => transition == transitionToReduce) != undefined;
            });

            if (placeCandidate == undefined) {
                continue;
            }

            return new ShortLoopReduction(placeCandidate, transitionToReduce);
        }

        return null;
    }
}
