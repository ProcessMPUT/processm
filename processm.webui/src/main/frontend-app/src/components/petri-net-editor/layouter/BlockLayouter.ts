import { PetriNetState } from '@/components/petri-net-editor/model/PetriNetState';
import { Layouter } from '@/components/petri-net-editor/layouter/interfaces/Layouter';
import { ReductionFinder } from '@/components/petri-net-editor/layouter/interfaces/ReductionFinder';
import { PlaceReductionFinder } from '@/components/petri-net-editor/layouter/finders/PlaceReductionFinder';
import { Reduction } from '@/components/petri-net-editor/layouter/interfaces/Reduction';
import { Block } from '@/components/petri-net-editor/layouter/blocks/Block';
import {
    AbstractionReductionFinder
} from '@/components/petri-net-editor/layouter/finders/AbstractionReductionFinder';
import { NodeBlock } from '@/components/petri-net-editor/layouter/blocks/NodeBlock';
import { Transition } from '@/components/petri-net-editor/model/Transition';
import { Place } from '@/components/petri-net-editor/model/Place';
import { SVGSelection } from '@/utils/Types';
import * as d3 from 'd3-selection';
import {
    TransitionReductionFinder
} from '@/components/petri-net-editor/layouter/finders/TransitionReductionFinder';
import { ShortLoopReductionFinder } from '@/components/petri-net-editor/layouter/finders/ShortLoopReductionFinder';

export class BlockLayouter implements Layouter {
    private static readonly REDUCTION_RULES: ReductionFinder[] = [
        new ShortLoopReductionFinder(),
        new TransitionReductionFinder(false),
        new TransitionReductionFinder(true),
        new AbstractionReductionFinder(),
        new PlaceReductionFinder()
    ];

    private static readonly MAX_SCORE: number = Math.max(...BlockLayouter.REDUCTION_RULES.map(finder => finder.getMaxScore()));

    private readonly _isDebug: boolean;
    private _blocks: Block[] = [];

    constructor(debug: boolean = false) {
        this._isDebug = debug;
    }

    public run(state: PetriNetState): PetriNetState {
        let reducedState = state.clone();
        reducedState.resetLayoutMap();

        this._blocks.forEach(block => block.delete());
        this._blocks = [];

        let bestScore = BlockLayouter.MAX_SCORE;
        while (bestScore > 0.0) {
            bestScore = 0.0;
            let bestReduction: Reduction | null = null;

            for (const reductionFinder of BlockLayouter.REDUCTION_RULES) {
                const [reduction, reductionScore] = reductionFinder.find(reducedState);
                if (reductionScore > bestScore) {
                    bestScore = reductionScore;
                    bestReduction = reduction;
                }

                if (reductionScore === BlockLayouter.MAX_SCORE) {
                    break;
                }
            }

            if (bestScore > 0.0) {
                const block = bestReduction!.apply(reducedState);
                this._blocks.push(block);
            }
        }

        const resultState = state.clone();
        this.applyLayout(reducedState, resultState);

        resultState.layoutMap = reducedState.layoutMap;

        return resultState;
    }

    public clearOverlay(): void {
        this._blocks.forEach(block => block.delete());
        this._blocks = [];
    }

    private applyLayout(state: PetriNetState, resultState: PetriNetState) {
        const svg: SVGSelection = d3.select('svg');

        const currentPosition = { x: 50.0, y: 50.0 };
        for (const block of state.getBlocks()) {
            block.applyLayout(currentPosition);
            if (this._isDebug) {
                block.render(svg);
            }

            currentPosition.y += block.height;
        }

        this.updateStatePositions(state, resultState);
    }

    private updateStatePositions(state: PetriNetState, resultState: PetriNetState) {
        state.getBlocks().forEach((block) => {
            this.updatePositionsRec(block, state, resultState);
        });
    }

    private updatePositionsRec(block: Block, state: PetriNetState, resultState: PetriNetState) {
        if (block instanceof NodeBlock) {
            const element = resultState.getElement(block.element.id);
            if (element instanceof Place) {
                element.cx = block.absoluteX + Place.RADIUS;
                element.cy = block.absoluteY + Place.RADIUS;
            } else if (element instanceof Transition) {
                element.x = block.absoluteX;
                element.y = block.absoluteY;
            }
        } else {
            block.blocks.forEach(block => this.updatePositionsRec(block, state, resultState));
        }
    }
}
