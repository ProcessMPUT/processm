import { ReductionFinder } from "@/components/petri-net-editor/layouter/interfaces/ReductionFinder";
import { PetriNetState } from "@/components/petri-net-editor/model/PetriNetState";
import { Reduction } from "@/components/petri-net-editor/layouter/interfaces/Reduction";
import { Transition } from "@/components/petri-net-editor/model/Transition";
import { TransitionReduction } from "../reductions/TransitionReduction";
import { Place } from "@/components/petri-net-editor/model/Place";

export class TransitionReductionFinder implements ReductionFinder {
  private readonly _isReversed: boolean;

  constructor(isReversed: boolean) {
    this._isReversed = isReversed;
  }

  find(state: PetriNetState): [Reduction | null, number] {
    let bestReduction: TransitionReduction | null = null;
    let bestScore = 0.0;

    const transitions = state.transitions;
    if (transitions.length < 2) {
      return [null, 0.0];
    }

    for (const candidateTransition of transitions) {
      const canBeReduced = this.evaluateTransitionForReduction(
        candidateTransition,
        state
      );
      if (!canBeReduced) {
        continue;
      }

      const solutionTransition = this.getSingleTransitionForBlock(
        state,
        candidateTransition,
        transitions
      );
      if (solutionTransition == null) {
        return [null, 0.0];
      }

      const solution: Transition[] = [solutionTransition];

      if (
        state.getPrePlaces(candidateTransition).length > 0 ||
        state.getPostPlaces(candidateTransition).length > 0
      ) {
        const reduction = new TransitionReduction(
          candidateTransition,
          solution,
          this._isReversed
        );
        const score = reduction.getScore(state);

        if (score === this.getMaxScore()) {
          return [reduction, score];
        }

        bestReduction = reduction;
        bestScore = score;
      }
    }

    return [bestReduction, bestScore];
  }

  getMaxScore(): number {
    return TransitionReduction.MAX_SCORE;
  }

  private evaluateTransitionForReduction(
    transition: Transition,
    state: PetriNetState
  ): boolean {
    const prePlaces = state.getPrePlaces(transition);
    if (prePlaces.some((place) => state.getPostTransitions(place).length < 2)) {
      return false;
    }

    const postPlaces = state.getPostPlaces(transition);
    return postPlaces.some(
      (place) => state.getPreTransitions(place).length >= 2
    );
  }

  private getSingleTransitionForBlock(
    state: PetriNetState,
    candidateTransition: Transition,
    transitions: Transition[]
  ): Transition | null {
    const prePlaces = new Set<Place>(state.getPrePlaces(candidateTransition));
    const postPlaces = new Set<Place>(state.getPostPlaces(candidateTransition));

    for (const transition of transitions) {
      if (transition == candidateTransition) {
        continue;
      }

      const prePlacesDiff = state
        .getPrePlaces(transition)
        .filter((place) =>
          !this._isReversed ? !prePlaces.has(place) : !postPlaces.has(place)
        );
      const postPlacesDiff = state
        .getPostPlaces(transition)
        .filter((place) =>
          !this._isReversed ? !postPlaces.has(place) : !prePlaces.has(place)
        );

      if (prePlacesDiff.length == 0 && postPlacesDiff.length == 0) {
        return transition;
      }
    }

    return null;
  }
}
