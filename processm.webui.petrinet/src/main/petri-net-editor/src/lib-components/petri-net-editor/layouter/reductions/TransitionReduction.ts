import { PetriNetState } from '@/lib-components/petri-net-editor/model/PetriNetState';
import { Reduction } from '@/lib-components/petri-net-editor/layouter/interfaces/Reduction';
import { Block } from '@/lib-components/petri-net-editor/layouter/blocks/Block';
import { Transition } from '@/lib-components/petri-net-editor/model/Transition';
import { Place } from '@/lib-components/petri-net-editor/model/Place';
import { VerticalBlock } from '@/lib-components/petri-net-editor/layouter/blocks/VerticalBlock';
import { PetriNetElement } from '@/lib-components/petri-net-editor/model/PetriNetElement';

export class TransitionReduction implements Reduction {
    static readonly MAX_SCORE: number = 1.0;

    private score: number = -1.0;
    private readonly _transitionToReduce: Transition;
    private readonly _transitions: Transition[];
    private readonly _isReversed: boolean;

    constructor(transitionToReduce: Transition, transitions: Transition[], isReversed: boolean) {
        this._transitionToReduce = transitionToReduce;
        this._transitions = transitions;
        this._isReversed = isReversed;
    }

    apply(state: PetriNetState): Block {

        const prePlaces = state.getPrePlaces(this._transitionToReduce);
        const postPlaces = state.getPostPlaces(this._transitionToReduce);

        let block: Block;
        if (this._transitions.length == 0) {
            const place: Place = prePlaces.length == 0
                ? postPlaces[0]
                : prePlaces[0];

            block = new VerticalBlock(
                state.getBlock(place.id),
                state.getBlock(this._transitionToReduce.id),
                this._isReversed
            );

            state.setBlock(place.id, block);
            state.removeBlock(place.id);
            return block;
        } else if (this._transitions.length > 1) {
            const prePlaces: Set<Place> = new Set<Place>();
            const postPlaces: Set<Place> = new Set<Place>();
            for (const transition of this._transitions) {
                state.getPrePlaces(transition).forEach(prePlaces.add);
                state.getPostPlaces(transition).forEach(postPlaces.add);
            }
            const diff: PetriNetElement[] = [...prePlaces].filter(place => postPlaces.has(place));
            diff.push(...this._transitions);

            let bestElement: PetriNetElement = diff[0];
            let bestWidth = 0;
            let bestHeight = 0;
            for (const element of diff) {
                const elementBlock = state.getBlock(element.id);
                if (TransitionReduction.isBetter(elementBlock, bestWidth, bestHeight)) {
                    bestElement = element;
                    bestWidth = elementBlock.width;
                    bestHeight = elementBlock.height;
                }
            }

            block = new VerticalBlock(
                state.getBlock(this._transitionToReduce.id),
                state.getBlock(bestElement.id),
                this._isReversed
            );
            state.setBlock(bestElement.id, block);
            state.removeBlock(bestElement.id);

            return block;
        } else if (!this._isReversed) {
            const transitionsIds = new Set<string>(this._transitions.map(transition => transition.id));
            let neighborTransition: Transition;
            if (state.getPrePlaces(this._transitionToReduce).length != 0) {
                const prePlace = state.getPrePlaces(this._transitionToReduce)[0];
                neighborTransition = state.getPostTransitions(prePlace)
                    .filter(transition => transitionsIds.has(transition.id))[0];
            } else if (state.getPostPlaces(this._transitionToReduce).length != 0) {
                const postPlace = state.getPostPlaces(this._transitionToReduce)[0];
                neighborTransition = state.getPreTransitions(postPlace)
                    .filter(transition => transitionsIds.has(transition.id))[0];
            } else {
                throw new Error(`Incorrect transition reduction`);
            }

            block = new VerticalBlock(
                state.getBlock(neighborTransition.id),
                state.getBlock(this._transitionToReduce.id),
                this._isReversed
            );
            state.setBlock(neighborTransition.id, block);
            state.removeBlock(this._transitionToReduce.id);
        } else {
            const transitionsIds = new Set<string>(this._transitions.map(transition => transition.id));
            let neighborTransition: Transition;
            if (state.getPostPlaces(this._transitionToReduce).length != 0) {
                const postPlace = state.getPostPlaces(this._transitionToReduce)[0];
                neighborTransition = state.getPostTransitions(postPlace)
                    .filter(transition => transitionsIds.has(transition.id))[0];
            } else if (state.getPrePlaces(this._transitionToReduce).length != 0) {
                const prePlace = state.getPrePlaces(this._transitionToReduce)[0];
                neighborTransition = state.getPreTransitions(prePlace)
                    .filter(transition => transitionsIds.has(transition.id))[0];
            } else {
                throw new Error(`Incorrect transition reduction`);
            }

            block = new VerticalBlock(
                state.getBlock(neighborTransition.id),
                state.getBlock(this._transitionToReduce.id),
                this._isReversed
            );
            state.setBlock(neighborTransition.id, block);
            state.removeBlock(this._transitionToReduce.id);
        }

        state.removeElement(this._transitionToReduce.id);
        return block;
    }

    getScore(state: PetriNetState): number {
        if (this.score > -1.0) {
            return this.score;
        }

        if (this._transitions.length == 0) {
            this.score = 1.0;
            return this.score;
        }
        const prePlacesAcceptable = state.getPrePlaces(this._transitionToReduce)
            .every(place => state.getPostTransitions(place).length != 1);

        if (!prePlacesAcceptable) {
            this.score = 0.0;
            return this.score;
        }

        const postPlacesAcceptable = state.getPostPlaces(this._transitionToReduce)
            .every(place => state.getPreTransitions(place).length != 1);

        if (!postPlacesAcceptable) {
            this.score = 0.0;
            return this.score;
        }

        this.score = TransitionReduction.MAX_SCORE / (this._transitions.length);
        return this.score;
    }

    private static isBetter(block: Block, bestWidth: number, bestHeight: number) {
        return block.height > bestHeight || (block.height == bestHeight && block.width > bestWidth);
    }
}
