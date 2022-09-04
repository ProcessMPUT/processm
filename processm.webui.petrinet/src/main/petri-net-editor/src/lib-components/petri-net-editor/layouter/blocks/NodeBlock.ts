import { PetriNetElement } from '@/lib-components/petri-net-editor/model/PetriNetElement';
import { Place } from '@/lib-components/petri-net-editor/model/Place';
import { Transition } from '@/lib-components/petri-net-editor/model/Transition';
import { Block } from '@/lib-components/petri-net-editor/layouter/blocks/Block';
import { SVGSelection } from '@/utils/Types';
import { Position } from '@/lib-components/petri-net-editor/layouter/interfaces/Position';

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

    render(_: SVGSelection): void {
    }

    delete(): void {
    }

    applyLayout(position: Position): void {
        this.absoluteX = position.x;
        this.absoluteY = position.y;
    }

    getNumberOfLayers(): number {
        return 1;
    }

    protected override calculatePositions(): void {
    }
}
