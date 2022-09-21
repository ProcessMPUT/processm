import { PetriNetElement } from "@/components/petri-net-editor/model/PetriNetElement";
import { Place } from "@/components/petri-net-editor/model/Place";
import { Transition } from "@/components/petri-net-editor/model/Transition";
import { Block } from "@/components/petri-net-editor/layouter/blocks/Block";
import { SVGSelection } from "@/utils/Types";
import { Position } from "@/components/petri-net-editor/layouter/interfaces/Position";

export class NodeBlock extends Block {
  element: PetriNetElement;

  constructor(element: PetriNetElement) {
    super([]);

    if (element instanceof Place) {
      this.width = 2 * Place.RADIUS;
      this.height = 2 * Place.RADIUS;
    } else if (element instanceof Transition) {
      this.width = Transition.WIDTH;
      this.height = Transition.HEIGHT;
    }

    this.element = element;
  }

  render(_: SVGSelection): void {}

  delete(): void {}

  applyLayout(position: Position): void {
    this.absoluteX = position.x;
    this.absoluteY = position.y;
  }

  get numberOfLayers(): number {
    return 1;
  }

  protected calculatePositions(): void {}
}
