import { PetriNetState } from "@/components/petri-net-editor/model/PetriNetState";
import { Reduction } from "@/components/petri-net-editor/layouter/interfaces/Reduction";
import { Place } from "@/components/petri-net-editor/model/Place";
import { VerticalBlock } from "@/components/petri-net-editor/layouter/blocks/VerticalBlock";
import { Block } from "@/components/petri-net-editor/layouter/blocks/Block";
import { Transition } from "@/components/petri-net-editor/model/Transition";

export class ShortLoopReduction implements Reduction {
  static readonly MAX_SCORE: number = 0.97;

  private readonly placeCandidate: Place;
  private readonly transitionsCandidate: Transition;
  private _score: number = -1.0;

  constructor(placeCandidate: Place, transitionCandidate: Transition) {
    this.placeCandidate = placeCandidate;
    this.transitionsCandidate = transitionCandidate;
  }

  apply(state: PetriNetState): Block {
    let block: Block;
    if (this.isPlaceReducible(state)) {
      block = new VerticalBlock(
        state.getBlock(this.placeCandidate.id),
        state.getBlock(this.transitionsCandidate.id),
        false
      );
      state.removeElement(this.placeCandidate.id);
      state.removeBlock(this.placeCandidate.id);
      state.setBlock(this.transitionsCandidate.id, block);
    } else if (this.isTransitionReducible(state)) {
      block = new VerticalBlock(
        state.getBlock(this.transitionsCandidate.id),
        state.getBlock(this.placeCandidate.id),
        false
      );
      state.removeElement(this.transitionsCandidate.id);
      state.removeBlock(this.transitionsCandidate.id);
      state.setBlock(this.placeCandidate.id, block);
    } else {
      throw new Error(`Incorrect single element loop reduction`);
    }

    return block;
  }

  getScore(state: PetriNetState): number {
    if (this._score > -1.0) {
      return this._score;
    }

    this._score =
      this.isPlaceReducible(state) || this.isTransitionReducible(state)
        ? ShortLoopReduction.MAX_SCORE
        : 0.0;
    return this._score;
  }

  private isPlaceReducible(state: PetriNetState): boolean {
    const preTransitions = state.getPreTransitions(this.placeCandidate);
    if (preTransitions.length != 1) {
      return false;
    }

    const postTransitions = state.getPostTransitions(this.placeCandidate);
    if (postTransitions.length != 1) {
      return false;
    }

    const preTransition = preTransitions.find(
      (transition) => transition == this.transitionsCandidate
    );

    const postTransition = postTransitions.find(
      (transition) => transition == this.transitionsCandidate
    );

    return preTransition != undefined && postTransition != undefined;
  }

  private isTransitionReducible(state: PetriNetState): boolean {
    const prePlaces = state.getPrePlaces(this.transitionsCandidate);
    if (prePlaces.length != 1) {
      return false;
    }

    const postPlaces = state.getPostPlaces(this.transitionsCandidate);
    if (postPlaces.length != 1) {
      return false;
    }

    const prePlace = prePlaces.find((place) => place == this.placeCandidate);

    const postPlace = postPlaces.find((place) => place == this.placeCandidate);

    return prePlace != undefined && postPlace != undefined;
  }
}
