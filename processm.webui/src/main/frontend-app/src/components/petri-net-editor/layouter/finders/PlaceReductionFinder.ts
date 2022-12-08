import { ReductionFinder } from "@/components/petri-net-editor/layouter/interfaces/ReductionFinder";
import { PetriNetState } from "@/components/petri-net-editor/model/PetriNetState";
import { PlaceReduction } from "@/components/petri-net-editor/layouter/reductions/PlaceReduction";
import { Reduction } from "@/components/petri-net-editor/layouter/interfaces/Reduction";

export class PlaceReductionFinder implements ReductionFinder {
  find(state: PetriNetState): [Reduction | null, number] {
    let bestReduction: PlaceReduction | null = null;
    let bestScore = 0.0;

    const places = state.places;
    if (places.length < 2) {
      return [null, 0.0];
    }

    for (const placeToReduce of places) {
      for (const place of places) {
        if (place === placeToReduce) {
          break;
        }

        const reduction = new PlaceReduction(placeToReduce, place);
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
    return PlaceReduction.MAX_SCORE;
  }
}
