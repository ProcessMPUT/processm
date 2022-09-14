import { Place, PlaceOptions } from '@/components/petri-net-editor/model/Place';
import { Transition, TransitionOptions } from '@/components/petri-net-editor/model/Transition';
import { Arc } from '@/components/petri-net-editor/model/Arc';
import { PetriNetElement } from '@/components/petri-net-editor/model/PetriNetElement';
import { Block } from '@/components/petri-net-editor/layouter/blocks/Block';
import { NodeBlock } from '@/components/petri-net-editor/layouter/blocks/NodeBlock';

export class PetriNetState {
    private _places: Map<string, Place> = new Map<string, Place>();
    private _transitions: Map<string, Transition> = new Map<string, Transition>();
    private _arcs: Map<string, Arc> = new Map<string, Arc>();
    private _elementArcs: Map<string, Arc[]> = new Map<string, Arc[]>();
    private _layoutMap: Map<string, Block> = new Map<string, Block>();

    clone(): PetriNetState {
        const newState = new PetriNetState();
        [...this._places.values()]
            .forEach(place => newState.createPlace(place.getOptions()));
        [...this._transitions.values()]
            .forEach(transition => newState.createTransition(transition.getOptions()));
        [...this._arcs.values()]
            .forEach(arc => newState.createArc(arc.outElementId, arc.inElementId, arc.id));

        return newState;
    }

    createPlace(options: PlaceOptions): Place {
        const place = Place.fromOptions(options);
        this._places.set(place.id, place);
        this._elementArcs.set(place.id, []);
        return place;
    }

    createTransition(options: TransitionOptions): Transition {
        const transition = Transition.fromOptions(options);
        this._transitions.set(transition.id, transition);
        this._elementArcs.set(transition.id, []);

        return transition;
    }

    createArc(outId: string, inId: string, id?: string): Arc | null {
        const outElement: PetriNetElement = this.getElement(outId);
        const inElement: PetriNetElement = this.getElement(inId);

        if (outElement instanceof Place && inElement instanceof Place) {
            return null;
        }
        if (outElement instanceof Transition && inElement instanceof Transition) {
            return null;
        }

        const arcIndex = this._elementArcs.get(outElement.id)!
            .findIndex((arc) => arc.inElementId == inElement.id);
        if (arcIndex >= 0) {
            return null;
        }

        const arc = new Arc(outElement.id, inElement.id, id);
        this._elementArcs.get(outElement.id)!.push(arc);
        this._elementArcs.get(inElement.id)!.push(arc);
        this._arcs.set(arc.id, arc);

        return arc;
    }

    getElementArcs(elementId: string): Arc[] {
        return this._elementArcs.get(elementId)!;
    }

    removeArc(arcId: string): void {
        const arc = this._arcs.get(arcId)!;

        this._elementArcs.set(arc.inElementId, this._elementArcs.get(arc.inElementId)!.filter((arc1) => arc1.id != arcId));
        this._elementArcs.set(arc.outElementId, this._elementArcs.get(arc.outElementId)!.filter((arc1) => arc1.id != arcId));
        this._arcs.delete(arcId);
    }

    removeElement(id: string): void {
        this._elementArcs.get(id)!.forEach((arc) => {
            this.removeArc(arc.id);
        });
        this._elementArcs.delete(id);

        if (this._places.has(id)) {
            this._places.delete(id);
        } else if (this._transitions.has(id)) {
            this._transitions.delete(id);
        } else {
            throw new Error(`Element (id="${id}") not found`);
        }
    }

    getElement(elementId: string): PetriNetElement {
        const element: PetriNetElement | undefined =
            this._places.get(elementId) ?? this._transitions.get(elementId);

        if (element == undefined) {
            throw new Error(`Element (id="${elementId}") not found`);
        }

        return element;
    }

    get places(): Place[] {
        return [...this._places.values()];
    }

    get transitions(): Transition[] {
        return [...this._transitions.values()];
    }

    get arcs(): Arc[] {
        return [...this._arcs.values()];
    }

    getPostPlaces(transition: Transition): Place[] {
        return this.getElementArcs(transition.id)
            .filter(arc => arc.outElementId == transition.id)
            .map(arc => this.getElement(arc.inElementId) as Place);
    }

    getPrePlaces(transition: Transition) {
        return this.getElementArcs(transition.id)
            .filter(arc => arc.inElementId == transition.id)
            .map(arc => this.getElement(arc.outElementId) as Place);
    }

    getPreTransitions(place: Place): Transition[] {
        return this.getElementArcs(place.id)
            .filter(arc => arc.inElementId == place.id)
            .map(arc => this.getElement(arc.outElementId) as Transition);
    }

    getPostTransitions(place: Place): Transition[] {
        return this.getElementArcs(place.id)
            .filter(arc => arc.outElementId == place.id)
            .map(arc => this.getElement(arc.inElementId) as Transition);
    }

    getPrePrePlaces(place: Place): Place[] {
        return this.getPreTransitions(place)
            .flatMap(transition => this.getPrePlaces(transition));
    }

    getPostPostPlaces(place: Place): Place[] {
        return this.getPostTransitions(place)
            .flatMap(transition => this.getPostPlaces(transition));
    }

    get layoutMap(): Map<string, Block> {
        return new Map<string, Block>(this._layoutMap);
    }

    set layoutMap(layoutMap: Map<string, Block>) {
        this._layoutMap = new Map<string, Block>(layoutMap);
    }

    resetLayoutMap(): void {
        this._layoutMap.clear();
        for (const element of [...this.places, ...this.transitions]) {
            this.setBlock(element.id, new NodeBlock(element));
        }
    }

    getBlock(elementId: string): Block {
        return this._layoutMap.get(elementId)!;
    }

    getBlocks(): Block[] {
        return [...this._layoutMap.values()];
    }

    setBlock(elementId: string, block: Block): void {
        this._layoutMap.set(elementId, block);
    }

    removeBlock(elementId: string): void {
        this._layoutMap.delete(elementId);
    }

    isCorrectNet(): [boolean, string] {
        const incorrectTransitions = this.transitions
            .filter(transition => {
                return this.getPrePlaces(transition).length == 0 || this.getPostPlaces(transition).length == 0;
            });

        let message: string | null = null;
        if (incorrectTransitions.length != 0) {
            const transitionTexts = incorrectTransitions.map(transition => transition.text).join(', ');
            message = `Incorrect transitions found: ${transitionTexts}`;
        }

        const incorrectPlaces = this.places
            .filter(place => {
                return this.getPreTransitions(place).length == 0 && this.getPostTransitions(place).length == 0;
            });

        if (incorrectPlaces.length != 0) {
            const placeTexts = incorrectPlaces.map(place => place.text).join(', ');
            if (message == null) {
                message = `Incorrect places found: ${placeTexts}`;
            } else {
                message += `\nIncorrect places found: ${placeTexts}`;
            }
        }

        return [message === null, message ?? ''];
    }
}
