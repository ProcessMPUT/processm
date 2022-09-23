import { PetriNetState } from "@/components/petri-net-editor/model/PetriNetState";
import { Reduction } from "@/components/petri-net-editor/layouter/interfaces/Reduction";
import { Place } from "@/components/petri-net-editor/model/Place";
import { Block } from "@/components/petri-net-editor/layouter/blocks/Block";
import { Transition } from "@/components/petri-net-editor/model/Transition";
import { HorizontalBlock } from "@/components/petri-net-editor/layouter/blocks/HorizontalBlock";

export class AbstractionReduction implements Reduction {
  static readonly MAX_SCORE: number = 0.99;
  transitionInTheMiddle: boolean = true;
  private readonly _placeToReduce: Place;
  private readonly _transitionToReduce: Transition;
  private readonly _preTransitions: Transition[];
  private readonly _postPlaces: Place[];
  private _score: number = -1.0;

  constructor(
    placeToReduce: Place,
    transitionToReduce: Transition,
    preTransitions: Transition[],
    postPlaces: Place[]
  ) {
    this._placeToReduce = placeToReduce;
    this._transitionToReduce = transitionToReduce;
    this._preTransitions = preTransitions;
    this._postPlaces = postPlaces;
  }

  apply(state: PetriNetState): Block {
    let bestTransition: Transition | null = null;
    let bestPlace: Place | null = null;
    if (
      this._preTransitions.length == 1 &&
      state.getPostPlaces(this._preTransitions[0]).length == 1
    ) {
      bestTransition = this._preTransitions[0];
    } else if (
      this._postPlaces.length == 1 &&
      state.getPreTransitions(this._postPlaces[0]).length == 1
    ) {
      bestPlace = this._postPlaces[0];
    } else {
      let bestValue = Infinity;
      for (const place of this._postPlaces) {
        const preTransitions = state.getPreTransitions(place);

        if (preTransitions.length < bestValue) {
          bestValue = preTransitions.length;
          bestTransition = null;
          bestPlace = place;
        }
      }
    }

    let block: HorizontalBlock;
    if (bestTransition != null) {
      block = new HorizontalBlock(
        state.getBlock(bestTransition.id),
        state.getBlock(this._placeToReduce.id),
        state.getBlock(this._transitionToReduce.id),
        false
      );
      state.setBlock(bestTransition.id, block);
    } else if (bestPlace != null) {
      block = new HorizontalBlock(
        state.getBlock(this._placeToReduce.id),
        state.getBlock(this._transitionToReduce.id),
        state.getBlock(bestPlace.id),
        true
      );

      state.setBlock(bestPlace.id, block);
      this.transitionInTheMiddle = false;
    } else {
      throw new Error("Could not find third element for abstraction reduction");
    }

    const preTransitions = state.getPreTransitions(this._placeToReduce);
    const postPlaces = state.getPostPlaces(this._transitionToReduce);
    for (const transition of preTransitions) {
      for (const place of postPlaces) {
        state.createArc(transition.id, place.id);
      }
    }
    this.reduceElements(state);

    return block;
  }

  getScore(_: PetriNetState): number {
    if (this._score >= 0) {
      return this._score;
    }

    // TODO: Should this be done also for _postPlaces?
    if (this._preTransitions.length == 0 && this._postPlaces.length == 0) {
      this._score = 0.0;
      return this._score;
    }

    // TODO: Verify if this normalizing factor works correctly
    let scoreDivisor: number =
      this._preTransitions.length + this._postPlaces.length;
    if (this._preTransitions.length != 0 && this._postPlaces.length != 0) {
      scoreDivisor /= 2;
    }
    this._score = AbstractionReduction.MAX_SCORE / scoreDivisor;

    return this._score;
  }

  private reduceElements(state: PetriNetState) {
    state.removeBlock(this._placeToReduce.id);
    state.removeBlock(this._transitionToReduce.id);

    state.removeElement(this._placeToReduce.id);
    state.removeElement(this._transitionToReduce.id);
  }
}
