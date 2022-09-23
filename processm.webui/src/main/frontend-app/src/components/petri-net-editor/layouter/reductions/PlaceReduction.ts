import { PetriNetState } from "@/components/petri-net-editor/model/PetriNetState";
import { Reduction } from "@/components/petri-net-editor/layouter/interfaces/Reduction";
import { Place } from "@/components/petri-net-editor/model/Place";
import { VerticalBlock } from "@/components/petri-net-editor/layouter/blocks/VerticalBlock";
import { Block } from "@/components/petri-net-editor/layouter/blocks/Block";

export class PlaceReduction implements Reduction {
  static readonly MAX_SCORE: number = 0.9;

  private readonly placeToReduce: Place;
  private readonly place: Place;
  private score: number = -1.0;

  constructor(placeToReduce: Place, place: Place) {
    this.placeToReduce = placeToReduce;
    this.place = place;
  }

  apply(state: PetriNetState): Block {
    const preTransitions = state.getPreTransitions(this.placeToReduce);
    for (const transition of preTransitions) {
      state.createArc(transition.id, this.place.id);
    }

    const postTransitions = state.getPostTransitions(this.placeToReduce);
    for (const transition of postTransitions) {
      state.createArc(this.place.id, transition.id);
    }

    // Delete reduced place
    state.removeElement(this.placeToReduce.id);

    const block = new VerticalBlock(
      state.getBlock(this.place.id),
      state.getBlock(this.placeToReduce.id),
      false
    );

    state.removeBlock(this.placeToReduce.id);
    state.setBlock(this.place.id, block);

    return block;
  }

  getScore(state: PetriNetState): number {
    if (this.score > -1.0) {
      return this.score;
    }

    const prePlaces1: Set<string> = new Set<string>(
      state.getPrePrePlaces(this.placeToReduce).map((place) => place.id)
    );
    const prePlaces2: Set<string> = new Set<string>(
      state.getPrePrePlaces(this.place).map((place) => place.id)
    );
    const postPlaces1: Set<string> = new Set<string>(
      state.getPostPostPlaces(this.placeToReduce).map((place) => place.id)
    );
    const postPlaces2: Set<string> = new Set<string>(
      state.getPostPostPlaces(this.place).map((place) => place.id)
    );

    const preIntersection = new Set<string>(
      [...prePlaces1].filter((place) => prePlaces2.has(place))
    );
    const postIntersection = new Set<string>(
      [...postPlaces1].filter((place) => postPlaces2.has(place))
    );

    const preUnion = new Set<string>([...prePlaces1, ...prePlaces2]);
    const postUnion = new Set<string>([...postPlaces1, ...postPlaces2]);

    this.score =
      (PlaceReduction.MAX_SCORE *
        (preIntersection.size + postIntersection.size)) /
      (preUnion.size + postUnion.size);
    return this.score;
  }
}
